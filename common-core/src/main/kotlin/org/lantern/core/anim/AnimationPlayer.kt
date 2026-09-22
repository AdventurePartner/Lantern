package org.lantern.core.anim

import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.lantern.core.anim.clip.ClipData
import org.lantern.core.anim.clip.Keyframe
import org.lantern.core.anim.clip.Vec3
import org.lantern.core.anim.control.AnimationControlStore
import org.lantern.core.anim.molang.MolangScope
import org.lantern.core.anim.molang.MolangVariableStore
import org.lantern.core.anim.molang.MutableMolangScope
import org.lantern.core.anim.molang.QueryNames
import org.lantern.core.anim.statemap.AnimationStateMapping
import org.lantern.core.anim.statemap.PlayMode
import org.lantern.core.anim.statemap.StateConfig
import org.lantern.core.action.ComboDef

/**
 * Lantern 自托管动画播放器（每实体一个）。
 *
 * 层栈模型：五层自下而上合成
 * LOCOMOTION(walk/idle) < ACTION_MOTION(跳跃/落地/服务端播控) < POSTURE(姿态链)
 * < ACTION_COMBAT(上身出招) < DEATH。
 * 每层单动画槽位（换片顶旧），层内换片交叉淡化，层的整体进出由可见度包络承担。
 *
 * 出招与运动分两层是「跳跃中攻击」「跑动中攻击」成立的前提：合在一层时单槽位
 * 无论怎么仲裁都只能二选一。配合 ACTION_COMBAT 的上身骨骼遮罩，
 * 腿归运动层、上身归出招层，两者天然并存。
 *
 * 层间合成：OVERRIDE 按骨骼控制集覆盖——上层动画「控制到的骨骼」覆盖下层，
 * 未控骨骼穿透（动画 json 的 bones 节点即控制集）；ADDING 数值逐轴叠加。
 * 因此行为兼容由资产天然保证：全身动画（现有 Boss 资产）OVERRIDE 掉下层全部骨骼，
 * 表现与单剪辑时代完全一致；只有资产刻意只控部分骨骼（如上身劈砍）时才出现
 * 「走路 + 上身动作」并存——这正是 P0 的能力目标。
 *
 * 判定链优先级原样保留，只是各分支不再互斥，而是写入各自层槽：
 * 死亡 -> DEATH 层；服务端播控 > 本地一次性(jump/landing/spawn) -> ACTION 层；
 * 姿态链 -> POSTURE 层；行走 -> LOCOMOTION 层。
 *
 * 状态表驱动（阶段三）：所有状态「配了才生效」，未配置直接穿透到下一优先级，
 * 因此旧 entityModels.yml（只配 idle/walk/...五字段）行为与扩容前完全一致。
 * 姿态链每帧按实体条件直选（ME 状态机语义——条件驱动，条件消失交叉淡化回当前正确状态）。
 *
 * 播放语义：LOOP 回环；ONCE/HOLD 时间轴相同（钳末帧），持有方式不同——
 * ONCE（一次性动作槽）播完回落，HOLD（pull_bow/hold_*）条件消失才回落。
 * speed 为负即倒放（reverse）：LOOP 回绕到尾部，ONCE 钳在首帧。
 *
 * 轴变换约定（反编译 GeckoLib 解析器实证）：旋转 (x,y,z) -> (-x,-y,+z) 弧度；
 * 位移、缩放原值。
 *
 * 平台协作：实体读取面收拢为每帧一份 [ActorSnapshot]（平台采集）；
 * 库查询/连招查询/事件上报/位移取消四个平台能力经构造器注入
 */
class AnimationPlayer(
    private val uuid: UUID,
    /** 播控指令自带动画库的剪辑查询（平台实现读客户端资源仓库） */
    private val clipLibrary: (String) -> Map<String, ClipData>?,
    /** 当前外观下生效的连招查询（平台实现组合外观 id 与连招定义表） */
    private val comboLookup: (UUID) -> ComboDef?,
    /** 动画生命周期事件上报（finish:seq / missing），平台实现桥到 C2S 通道 */
    private val eventSink: (UUID, String, String) -> Unit,
    /** 本地玩家的动作位移取消（播控顶掉本地动作时，本地实体才需要停位移） */
    private val dashCancel: (UUID) -> Unit
) {

    private companion object {
        // 垂直速度窗口阈值（格/秒）：真实跳跃初速约 8.9、台阶行走约 1.7——
        // 阈值取 2.0/-2.0：台阶与碰撞微弹不误判（防卡脚），起跳触发延迟也最小
        const val RISE_VY = 2.0
        const val FALL_VY = -2.0
        const val GROUNDED_VY = 0.5
        // 窗口位移超过该值视为传送/闪现：重置基准点不产生边沿（防误触发 jump/landing）
        const val TELEPORT_DISTANCE_SQ = 25.0
        // 剪辑长度未知时一次性动作的持有上限（兜底防锁死；landing 语义上限 0.5s 由剪辑长度天然满足）
        const val LOCAL_ACTION_FALLBACK_MS = 500L
        // 姿态状态最小驻留（「别打断当前状态」防抖的内核化）：判定边界抖动（潜行微移的
        // moving 边界、水边、空中边沿）会让 POSTURE 层换片再换回，进入型过渡动画
        // （蹲下过程 hold）每次被重新选中都从头播——驻留期内保持当前状态不切换
        const val POSTURE_DWELL_MS = 150L
    }

    /**
     * 层种类，ordinal 即合成顺序（低 -> 高）。
     *
     * ACTION 拆成两层是「跳跃中攻击」「跑动中攻击」成立的前提：单槽位无论怎么仲裁
     * 都只能二选一——让攻击顶掉跳跃则起跳看不见，让跳跃挡住攻击则挥砍看不见。
     * 拆开后 ACTION_MOTION 管全身运动（跳跃/落地/服务端播控），ACTION_COMBAT 管
     * 上身出招，各占各的槽位天然并存。
     *
     * 层序理由：运动动作压过行走；上身姿态（持盾/拉弓）压过运动动作的手臂；
     * 出招压过一切姿态。
     */
    private enum class LayerKind { LOCOMOTION, ACTION_MOTION, POSTURE, ACTION_COMBAT, HEAD, DEATH }

    /**
     * 上身骨骼集：ACTION_COMBAT 层在这些骨骼上满权重，其余按腿部归属让位给下层。
     * 由服务端状态表下发（见 AnimationStateMapping.upperBodyBones），
     * 每帧从当前状态表刷新——换外观就换骨架，遮罩要跟着走
     */
    private var upperBodyBones: Set<String> = AnimationStateMapping.DEFAULT_UPPER_BODY_BONES

    /**
     * 腿部归属系数：0 = 出招动画的腿完整播放，1 = 腿完全交还给下层（行走/跳跃）。
     *
     * 出招层的腿部不能硬裁剪：攻击资产里躯干前倾与腿部蹬地是协同编制的，
     * 无条件丢弃腿部轨道会让上下半身脱节，静止出招更会整条腿被锁死在待机姿态。
     * 所以按「下层是否真的需要这条腿」插值——原地出招腿跟着动作走，
     * 跑动/跳跃中出招才把腿让出去，两端之间 150ms 平滑过渡避免起步停步时突变
     */
    private var legHandoff = 0f

    /** 层间混合模式：OVERRIDE 按控制集覆盖（未控骨骼穿透）；ADDING 数值逐轴叠加 */
    private enum class BlendType { OVERRIDE, ADDING }

    /**
     * 单层状态。两套淡化机制职责分离（P0 修复的核心）：
     *
     * - **层内换片交叉淡化**（fadeClip 组）：同层旧片 -> 新片，双方都属于本层，
     *   在层内部把姿势混合后再交给层合成。
     * - **层可见度包络**（envelope 组）：本层整体在合成中的占比，空层切入 0->1、
     *   清层 1->0。它作用在层合成的权重上，因此「本层没有控制的骨骼」完全不参与，
     *   下层保持原样。
     *
     * 旧实现把空层切入也做成层内淡化（以上一帧**全合成**姿态为起点），跨层的量被
     * 用在层内：攻击这类窄控制面动画切入时，快照里的腿骨只存在于 fade 起点侧，
     * 被按 (1-w) 衰减后又以满权重覆盖下层的行走腿——过渡期腿被拉直、过渡结束
     * 突然跳回步态，即「运动中出招卡脚」。包络淡入没有这个问题：
     * 攻击不控的骨骼根本不在本层姿势里，层合成时原样穿透。
     *
     * blendSeconds 记录最近一次进入过渡，层清空时作为退出淡化时长。
     */
    private class LayerState(val kind: LayerKind) {
        var clip: ClipData? = null
        var time = 0f
        var speed = 1f
        var loops = true

        var blend: BlendType = BlendType.OVERRIDE
        /** 层静态权重（配置驱动，预留给按状态配 weight；当前恒 1） */
        var weight = 1f
        var blendSeconds = 0.25f

        // 层内交叉淡化：换片时旧片继续推进并在过渡时长内淡出
        var fadeClip: ClipData? = null
        var fadeTime = 0f
        /** 旧片进入淡出时的播放速度——步速调制下两片必须各按各的速度推进，否则混合期相位发散 */
        var fadeSpeed = 1f
        var fadeElapsed = -1f
        var fadeDuration = 0.25f
        var fadeLoops = true

        // 层可见度包络：0 = 完全不参与合成，1 = 满权重
        var envelope = 0f
        var envelopeTarget = 0f
        /** 包络变化速率（每秒），由过渡时长换算；<=0 视为瞬时 */
        var envelopeRate = 0f
    }

    private val layers = LayerKind.entries.map { layer ->
        LayerState(layer).apply {
            // 头部层是唯一的叠加层：视角跟随要加在既有姿态之上，而不是覆盖它——
            // 覆盖会让走路时的头部摆动整个消失，只剩僵硬的视角朝向
            if (layer == LayerKind.HEAD) blend = BlendType.ADDING
        }
    }
    private val locomotion get() = layers[LayerKind.LOCOMOTION.ordinal]
    private val actionMotion get() = layers[LayerKind.ACTION_MOTION.ordinal]
    private val posture get() = layers[LayerKind.POSTURE.ordinal]
    private val actionCombat get() = layers[LayerKind.ACTION_COMBAT.ordinal]
    private val head get() = layers[LayerKind.HEAD.ordinal]
    private val death get() = layers[LayerKind.DEATH.ordinal]

    private var channelId = -1L
    private var lastFinishReportedId = -1L
    private var dead = false
    private var lastNanos = 0L

    /**
     * 本地一次性动作的两个独立槽位，分别对应两个 ACTION 层。
     * 运动槽（jump/sprint_jump/landing/spawn）与出招槽（attack_*）互不抢占，
     * 于是跳跃与挥砍可以同时进行——腿走跳跃、上身走攻击。
     *
     * restart 标记：本次是新触发（而非连续帧沿用），让该层重启时间轴——
     * 同一动画连续两次触发（连击两刀同片）必须从头播，不能被「同片早退」吞掉
     */
    private class ActionSlot {
        /** 直接持剪辑而非状态名：外部注入的动作（按键触发的翻滚等）来自独立动画文件，
         *  不在 costume 的状态表里、按名查不到——持剪辑让两种来源走同一条路 */
        var clip: ClipData? = null
        var transitionSeconds = 0.1f
        var loops = false
        var speed = 1f
        var deadline = 0L
        var restart = false
        /** 霸体：播放期间拒绝被同槽的新动作顶替（翻滚这类无敌帧动作需要） */
        var uninterruptible = false
        /**
         * 独占全身：播放期间压制出招层。翻滚这类动作是整个身体的位移演出，
         * 上半身若还在挥刀就成了两套动作各演各的——出招层比运动层高，
         * 不显式压制就会盖掉翻滚的上半身
         */
        var exclusive = false
        /**
         * 本动作播完后的退出过渡秒数。
         * 跳跃类末帧≈站立、必须快速让位（否则落地后与下层跌落定格叠加，观感是又播一遍），
         * 所以内部状态动作沿用 50ms；翻滚这类末帧姿态与步态差距大的外部动作，
         * 50ms 硬切会露出一帧突变，由配置自带退出时长
         */
        var exitSeconds = 0.05f
    }

    private val motionSlot = ActionSlot()
    private val combatSlot = ActionSlot()

    var pose: Map<String, FloatArray> = emptyMap()
        private set

    /** 诊断（P1Diag）：状态表 + 各层实时剪辑 + 运动判定实测 */
    fun describeDiagnostic(): String {
        val layersInfo = layers.joinToString(" ") { layer ->
            "${layer.kind.name}=${layer.clip?.name ?: "-"}@${String.format("%.2f", layer.time)}" +
                (if (layer.fadeClip != null) "(fade)" else "")
        }
        return "$layersInfo mov=${if (movingCached) 1 else 0} v=${String.format("%.1f", verticalSpeed)} air=${if (airborne) 1 else 0} " +
            "posture=$postureState dwell=${System.currentTimeMillis() - postureSinceMs}ms"
    }

    // 位移实测的运动检测：客户端远程实体的 deltaMovement 不同步，用实际位移判定；
    // 结果在采样窗口间保持，双阈值滞回避免边界闪烁。同一窗口顺带产出垂直速度
    // （onGround 也不同步），驱动 jump/landing 边沿与 falling 姿态
    private var lastPos: DoubleArray? = null
    private var lastPosMs = 0L
    private var movingCached = false
    private var airborne = false
    private var wasRising = false
    private var everFell = false
    private var airSinceMs = 0L
    private var jumpEdge = false
    private var landingEdge = false

    // 最近一次采样窗口的实测速度（molang query: ground_speed / vertical_speed）
    private var groundSpeed = 0f
    private var verticalSpeed = 0f

    // 状态驻留（两组独立：LOCOMOTION 全身状态机 / POSTURE 上身姿态）
    private var locoState: String? = null
    private var locoSinceMs = 0L
    private var postureState: String? = null
    private var postureSinceMs = 0L

    // sprint 进入滞回与攻击交替
    private var sprintMs = 0f
    private var sprintActive = false
    private var wasSwinging = false
    private var prevAttackAnim = 0f
    private var attackToggle = false
    /** 待处理的攻击输入（只记有无，左右在起播瞬间才决定——见 tryStartAttack） */
    private var queuedAttack = false
    private var queuedAttackExpireMs = 0L

    /**
     * 求值上下文的 query 表。
     * 采样每一层时把 anim_time 换成该层自己的时间轴——全局取「最高非空层」会在
     * 层切换的那一帧突然跳到另一条时间线上，依赖 anim_time 的表达式关键帧
     * （呼吸、摆动这类）会随之抖一下
     */
    private var evalQueries: MutableMolangScope? = null

    /**
     * 播控剪辑解析：指令自带动画库就从那个库取，否则用目标当前绑定的库。
     * 技能剪辑与外观库解耦的落点在这里——库随主手物品切换，指令自带库就不受影响
     */
    private fun forcedClip(
        forced: AnimationControlStore.ForcedAnimation,
        clips: Map<String, ClipData>
    ): ClipData? {
        val library = forced.library
        if (library != null) {
            return clipLibrary(library)?.get(forced.animation)
        }
        return clips[forced.animation]
    }

    /** 播控此刻是否占着全身运动层 / 上身出招层（每帧刷新，occupy 据此拒绝本地动作） */
    private var forcedOwnsMotion = false
    private var forcedOwnsCombat = false

    /** 连招段位（-1 = 未进入连招）与上次出手时刻，用于取消窗口与输入窗口判定 */
    private var comboIndex = -1
    private var comboLastTriggerMs = 0L
    /** 当前段到达可取消点的时刻（毫秒），输入窗口从这里起算 */
    private var comboCancelReadyMs = 0L
    /** 上一轮生效的连招 id：换组即重置段位，避免用新组的招式接旧组的段 */
    private var comboSetId: String? = null

    /** 当前外观下生效的连招（随动画组切换而变） */
    private fun currentCombo() = comboLookup(uuid)

    fun drive(
        clips: Map<String, ClipData>,
        forced: AnimationControlStore.ForcedAnimation?,
        actor: ActorSnapshot,
        states: AnimationStateMapping,
        firstRender: Boolean
    ) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1_000_000_000f, 0.25f)
        lastNanos = now
        upperBodyBones = states.upperBodyBones
        val isMoving = computeMotion(actor.x, actor.y, actor.z, actor.physicallyGrounded)
        // 攀爬中位移实测的垂直速度（约 2.36 格/秒）会越过起跳/跌落阈值——
        // 梯子/藤蔓上强制清空中状态并拦截边沿，否则爬梯被演成连续跳跃。
        // 创造飞行同理且更甚：按空格上升的垂直速度远超起跳阈值，会被读成连续起跳，
        // 下降超过 600ms 还会进跌落——飞行期间整条空中判定必须停用
        val onClimb = actor.climbing
        val flying = actor.flying
        if (onClimb || flying) {
            airborne = false
            wasRising = false
            everFell = false
            airSinceMs = 0L
            jumpEdge = false
            landingEdge = false
        }

        // --- 死亡状态（进入时锁定，停末帧） ---
        if (actor.living && actor.dead) {
            dead = true
        } else if (dead) {
            dead = false
        }

        // --- 播控暂停：冻结一切时间推进（时间轴/淡化/once 墙钟到期——
        //     暂停中的动画既不该结束也不该继续淡入淡出） ---
        val paused = AnimationControlStore.isPaused(uuid)

        // --- 播控到期（once）：ACTION 层时间轴到达剪辑末尾即刻释放；
        //     时间戳兜底仅在剪辑缺失/长度异常时生效。持有末帧等待到期戳会造成
        //     "动画播完仍僵立收招"的空窗（Boss 已在走路而画面停在站姿） ---
        // --- 播控剪辑缺失：立刻放弃，不占层。
        //     原先靠 10 秒兜底到期：这十秒里运动层空置、本地动作全被封死，到期还会
        //     为一段从未播过的动画上报 finish 触发链式接续。缺剪辑的常见原因是库切换
        //     （外观随主手物品换了）或名字写错，两者都不该让层栈卡死 ---
        if (forced != null && forcedClip(forced, clips) == null) {
            if (lastFinishReportedId != forced.id) {
                lastFinishReportedId = forced.id
                eventSink(uuid, forced.animation, "missing")
            }
            AnimationControlStore.stop(uuid, forced.animation)
        }
        val forcedAlive = AnimationControlStore.get(uuid)
        if (!paused && forcedAlive != null && !forcedAlive.loop) {
            val forcedClipData = forcedClip(forcedAlive, clips)
            val forcedLayer = if (forcedAlive.toCombatLayer) actionCombat else actionMotion
            val timelineEnded = forcedClipData != null && forcedClipData.length > 0f &&
                channelId == forcedAlive.id && forcedLayer.clip === forcedClipData && forcedLayer.time >= forcedClipData.length
            val timestampExpired = forcedAlive.expiresAtMs > 0 &&
                System.currentTimeMillis() >= forcedAlive.expiresAtMs
            if (timelineEnded || timestampExpired) {
                if (lastFinishReportedId != forcedAlive.id) {
                    lastFinishReportedId = forcedAlive.id
                    // 回带服务端序号：连点同一技能时上一实例的 finish 才不会撞掉新实例
                    val event = if (forcedAlive.seq >= 0L) "finish:${forcedAlive.seq}" else "finish"
                    eventSink(uuid, forcedAlive.animation, event)
                }
                if (forcedAlive.animation != states.death) {
                    AnimationControlStore.stop(uuid, forcedAlive.animation)
                }
                // 死亡动画播完后持有末帧等待真死亡（服务端 finish 回报 -> health=0），
                // 不回落行走——否则真死亡晚于回落到达时会把 die 从头重播"再死一次"
            }
        }
        val activeForced = AnimationControlStore.get(uuid)

        // --- 本地一次性动作到期：ACTION 层时间轴播完即释放；被播控覆盖时由截止时间兜底。
        //     物理滞空期间持有末帧：跳跃动画时长与滞空有偏差（触发延迟），播完即清层
        //     会在落地前露出"跌落"定格（收腿姿态闪现=观感"跳跃播了一次半"）——
        //     跳跃末帧≈站立，覆盖到真落地再快切即无空窗 ---
        // 运动槽：物理滞空期间持有末帧（跳跃动画时长与滞空有偏差），真落地才释放
        motionSlot.clip?.let { clip ->
            // 收尾与下层交叠：提前一个「退出过渡」的时长释放槽位，淡出就发生在动作的
            // 尾段里，而不是等整段播完、定格一帧、再开始淡。播完再淡 = 收势处僵一下
            // 再硬切，正是翻滚落地那一下发死的来源。交叠上限取剪辑的一半，
            // 短动作不至于刚起手就开始退
            val overlap = motionSlot.exitSeconds.coerceIn(0f, clip.length * 0.5f)
            val timelineEnded = clip.length > 0f &&
                actionMotion.clip === clip && actionMotion.time >= clip.length - overlap
            // 空中持有末帧只对跳跃类成立：翻滚这类自带时长的动作播完即收，
            // 否则空中翻滚会一直挂到落地。按状态名判而不是按霸体标记判——
            // 非霸体的空中翻滚、落地、出生动作原先也会被钉到落地
            val isJumpClip = clip.name == states.config("jump")?.animation ||
                clip.name == states.config("sprint_jump")?.animation
            val holdInAir = airborne && isJumpClip
            if ((timelineEnded && !holdInAir) || System.currentTimeMillis() >= motionSlot.deadline) {
                releaseMotionSlot()
            }
        }
        // 出招槽：时间轴播完即释放，不受滞空影响
        combatSlot.clip?.let { clip ->
            val timelineEnded = clip.length > 0f &&
                actionCombat.clip === clip && actionCombat.time >= clip.length
            if (timelineEnded || System.currentTimeMillis() >= combatSlot.deadline) {
                combatSlot.clip = null
                combatSlot.uninterruptible = false
            }
        }

        // --- 排队攻击消费：当前刀进度达 70% 即可接刀（收势段可取消——combo
        //     cancel_at 语义；主砍段始终完整）。不排队则冷却一到就打断换片，
        //     每刀腰斩成快进；等播完则节奏偏慢（这批砍击不是蓄力重型） ---
        if (queuedAttack) {
            val expired = System.currentTimeMillis() >= queuedAttackExpireMs
            val curClip = actionCombat.clip
            // 接刀阈值：配了连招读当前段自己的 cancel-at，没配回落 70%
            val cancelAt = currentCombo()?.steps?.getOrNull(comboIndex)?.cancelAt ?: 0.7f
            val canChain = combatSlot.clip == null ||
                (curClip != null && actionCombat.time >= curClip.length * cancelAt)
            val consumed = canChain && if (currentCombo() != null) {
                advanceCombo()
            } else {
                tryStartAttack(states, clips)
            }
            if (consumed || expired) queuedAttack = false
        }

        // --- 边沿触发：spawn（实体生命周期内首次渲染）/ jump（起跳）/ landing（落地）。
        //     jump/landing 排除水中与骑乘：游泳上浮、载具颠簸同样产生垂直速度边沿，
        //     且 ACTION 层高于姿态链，swim/riding 姿态拦不住误触发 ---
        if (firstRender) {
            states.config("spawn")?.let { startMotionAction(it, clips) }
        }
        val edgeAllowed = actor.living && !actor.inWater && !actor.passenger && !onClimb && !flying
        if (jumpEdge) {
            jumpEdge = false
            if (edgeAllowed && actor.living) {
                // 疾跑中起跳优先 sprint_jump，未配置则回退 jump
                val jumpConfig = (if (actor.sprinting) {
                    resolveState(states, clips, "sprint_jump", "jump")
                } else {
                    resolveState(states, clips, "jump")
                })?.second
                jumpConfig?.let { startMotionAction(it, clips) }
            }
        }
        if (landingEdge) {
            landingEdge = false
            if (edgeAllowed) {
                states.config("landing")?.let { startMotionAction(it, clips) }
                // 物理落地即收势：跳跃类一次性动作的"展体落地"尾段快进掉——
                // 起跳触发有采样延迟，尾段约 0.15s 会拖到物理落地之后在地面播放，
                // 与空中段劈成两截（"起跳播一次、落地又一次"的观感来源）
                val la = motionSlot.clip
                if (la != null) {
                    val isJumpAnim = la.name == states.config("jump")?.animation ||
                        la.name == states.config("sprint_jump")?.animation
                    // 层上此刻必须真的是这段跳跃：播控占层时 actionMotion.clip 是技能剪辑，
                    // 不加这一判会把技能快进到末帧、当帧上报 finish、服务端伤害帧全部作废
                    if (isJumpAnim) {
                        actionMotion.clip?.let { c ->
                            if (c === la && c.length > 0.08f) actionMotion.time = max(actionMotion.time, c.length - 0.08f)
                        }
                    }
                    // 落地瞬间强制同步 LOCOMOTION：若仍停在跌落，直接零过渡换地面兜底，
                    // 消除落地判定与状态切换之间的任何帧错位（跌落定格裸露一帧即"半次"观感）
                    if (locomotion.clip != null && locomotion.clip!!.name == states.config("falling")?.animation) {
                        resolveState(states, clips, if (movingCached) "walk" else "idle")?.let { (c, cfg, _) ->
                            setLayerTarget(locomotion, c, 0f, loops = cfg.playMode == PlayMode.LOOP, speed = 1f)
                        }
                    }
                }
            }
        }

        // --- 攻击边沿：swinging 上升沿（首击）+ attackAnim 锯齿回落（连击重挥——
        //     连点时 swinging 恒 true，只有进度从高位跳回 0；每次真实挥击恰好一个
        //     跳变，无同步杂波——swingTime 的刷新抖动会产生假边沿，弃用） ---
        val swinging = actor.swinging
        val attackAnimNow = actor.attackAnim
        val swingEdge = !wasSwinging && swinging
        // attackAnim = swingTime / getCurrentSwingDuration()，挥击期间单调递增，
        // 任何下降都只可能是重挥重置，所以阈值只需躲开浮点相等、不需要留大余量。
        // 原先的 0.4 是按「最早重挥出现在进度 0.5 处」取的，但 duration 受急迫效果
        // 缩短（急迫 II 为 3 tick），跳变只有 0.33，整段连击的边沿会被漏掉
        val swingRestart = swinging && attackAnimNow < prevAttackAnim - 0.05f
        if (swingEdge || swingRestart) {
            // 当前刀未播完时排队而非打断——冷却节奏(0.625s)快于刀长(0.75s)，
            // 直接换片会把每刀腰斩成"快进连播"。出招槽独立于运动槽，
            // 所以起跳/落地不再吞掉攻击输入（跳跃中挥砍两者并存）
            val attackClip = actionCombat.clip
            val attackInProgress = combatSlot.clip != null && attackClip != null &&
                actionCombat.time < attackClip.length
            // 配了连招就走段位推进，否则回落左右交替（旧行为，向后兼容）
            val taken = if (currentCombo() != null) {
                advanceCombo()
            } else {
                !attackInProgress && tryStartAttack(states, clips)
            }
            if (!taken) {
                // 队列只记「有一次待处理的输入」，不记左右——左右在真正起播时才决定，
                // 否则疯狂点击时单格队列被反复覆盖，落到哪一边全看点击时序，
                // 表现就是连续几次同一边。
                // 过期至少撑到当前段的可取消点：蓄力段取消点在 1.2s，500ms 的队列会把
                // 前半段的所有输入丢掉
                queuedAttack = true
                queuedAttackExpireMs = max(System.currentTimeMillis() + 500L, comboCancelReadyMs + 150L)
            }
        }
        wasSwinging = swinging
        prevAttackAnim = attackAnimNow

        // --- 分层直选：各判定分支写入各自层槽，不再互斥 ---
        // DEATH 层：死亡剪辑（进入即钉末帧）；存活时清层（退出淡化露出下层）
        val deathConfig = states.config("death")
        val deathTarget = if (dead) states.death?.let { clips[it] } else null
        setLayerTarget(
            death, deathTarget,
            (deathConfig?.transition ?: 5) * 0.05f,
            loops = false, speed = 1f,
            pinLastFrame = true
        )

        // 服务端播控按指令归层：combat = 上身出招层（腿保持移动，可边跑边放技能），
        // 缺省进全身运动层（压住行走，技能演出的常态）
        val forcedToCombat = activeForced?.toCombatLayer == true
        val forcedForMotion = if (forcedToCombat) null else activeForced

        // 播控占层标记：occupy 据此拒绝本地动作。全身播控同时视为独占——
        // 技能演出期间不该再起本地翻滚，也不该让连招盖住上半身
        forcedOwnsMotion = forcedForMotion != null
        forcedOwnsCombat = forcedToCombat
        if (forcedForMotion != null) {
            if (motionSlot.clip != null) {
                // 被播控顶掉的本地动作直接作废：留在槽里会在播控结束后从第 0 帧补播一遍，
                // 它的位移也要一起停——动画看不见、人却在滑
                releaseMotionSlot()
                dashCancel(uuid)
            }
            if (combatSlot.clip != null) {
                combatSlot.clip = null
                combatSlot.uninterruptible = false
            }
            queuedAttack = false
            // 播完清层用指令给的退出过渡，不借用上一次翻滚残留的收尾时长
            motionSlot.exitSeconds = forcedForMotion.exitSeconds
        }

        // ACTION_MOTION 层（全身运动）：服务端播控 > 本地运动动作；都无时清层
        when {
            forcedForMotion != null -> {
                // 同名动画再次下发（新 id）= 重放：重置时间轴从头播，
                // 否则 target===clip 早退会让第二次播放被顶旧片且立即误报 finish
                val restart = forcedForMotion.id != channelId
                if (restart) channelId = forcedForMotion.id
                setLayerTarget(
                    actionMotion, forcedClip(forcedForMotion, clips),
                    // 播控切入的过渡时长由服务端指令指定（tick × 50ms）；0 = 立即切换（如死亡）。
                    // 协议默认 5 tick = 0.25s，与行走层淡化时长一致，技能动画观感不变
                    forcedForMotion.transition * 0.05f,
                    loops = forcedForMotion.loop, speed = forcedForMotion.speed,
                    forceRestart = restart
                )
            }
            motionSlot.clip != null -> {
                setLayerTarget(
                    actionMotion, motionSlot.clip,
                    motionSlot.transitionSeconds,
                    loops = motionSlot.loops, speed = motionSlot.speed,
                    forceRestart = motionSlot.restart
                )
                motionSlot.restart = false
            }
            // 清层淡出压到 50ms：一次性动作（跳跃等）末帧≈站立，快速让位；
            // 长淡出会在落地后与下层"跌落"定格姿态叠加约半秒——观感即"落地又播一遍"
            else -> setLayerTarget(actionMotion, null, motionSlot.exitSeconds, loops = false, speed = 1f)
        }

        // ACTION_COMBAT 层（上身出招）：与运动层并行，互不抢占
        if (forcedToCombat && activeForced != null) {
            // 上身技能：压过本地出招，腿部继续由下层的移动状态驱动
            if (combatSlot.clip != null) {
                combatSlot.clip = null
                combatSlot.uninterruptible = false
                queuedAttack = false
            }
            combatSlot.exitSeconds = activeForced.exitSeconds
            val restart = activeForced.id != channelId
            if (restart) channelId = activeForced.id
            setLayerTarget(
                actionCombat, forcedClip(activeForced, clips),
                activeForced.transition * 0.05f,
                loops = activeForced.loop, speed = activeForced.speed,
                forceRestart = restart
            )
        } else if (combatSlot.clip != null) {
            setLayerTarget(
                actionCombat, combatSlot.clip,
                combatSlot.transitionSeconds,
                loops = combatSlot.loops, speed = combatSlot.speed,
                forceRestart = combatSlot.restart
            )
            combatSlot.restart = false
        } else {
            setLayerTarget(actionCombat, null, combatSlot.exitSeconds, loops = false, speed = 1f)
        }

        // HEAD 层（叠加）：配了 head 状态即常驻。动画内用 query.pitch / query.yaw
        // 取视角角度，于是「头随视角、身体照常走路」纯配置即可成立。
        // 死亡时停掉——尸体不该还在东张西望
        val headPick = if (dead) null else states.config("head")?.let { cfg ->
            clips[cfg.animation]?.let { cfg to it }
        }
        if (headPick != null) {
            setLayerTarget(
                head, headPick.second,
                headPick.first.transition * 0.05f,
                loops = headPick.first.playMode == PlayMode.LOOP, speed = 1f
            )
        } else {
            setLayerTarget(head, null, head.blendSeconds, loops = false, speed = 1f)
        }

        // POSTURE 层：上身姿态链（use 弓/盾/食、hold_* 持物——窄控制面，
        // 与 LOCOMOTION 的移动状态并存：走路拉弓=腿走路+手臂拉弓）
        val posturePick = if (actor.living) selectUpperBodyPosture(actor, states, clips) else null
        if (posturePick != null) {
            setLayerTarget(
                posture, posturePick.first,
                posturePick.second.transition * 0.05f,
                loops = posturePick.second.playMode == PlayMode.LOOP, speed = 1f
            )
        } else {
            if (postureState != null) postureState = null
            setLayerTarget(posture, null, posture.blendSeconds, loops = false, speed = 1f)
        }

        // LOCOMOTION 层：全身移动状态机（idle/walk/sprint/swim/ride/sneak/fly/falling
        // 互斥切换+crossfade——全身动画绝不能双层同播，否则两套步态逐帧混合=部件分离）
        if (activeForced == null) channelId = -1L
        // sprint 进入滞回：isSprinting 边界抖动会让 walk<->sprint 反复换片（fade 重开=顿挫），
        // 需持续 150ms 才进入、条件消失即刻退出
        if (actor.living && actor.sprinting && isMoving) {
            sprintMs += dt
            if (sprintMs >= 0.15f) sprintActive = true
        } else {
            sprintMs = 0f
            sprintActive = false
        }
        val locomotionPick = if (actor.living) selectLocomotion(actor, isMoving, states, clips) else null
            ?: resolveState(states, clips, if (isMoving) "walk" else "idle")
        locomotionPick?.let { (clip, config, key) ->
            // 步频按实测速度调制：剪辑按基准速度编制，慢走慢摆、快走快摆，
            // 消除"慢速移动配全速步态"的滑步感（setLayerTarget 同片时每帧更新 speed）
            val speed = when (key) {
                "walk" -> (groundSpeed / 4.3f).coerceIn(0.5f, 1.5f)
                "sneak_walk" -> (groundSpeed / 1.31f).coerceIn(0.5f, 1.5f)
                "sprint" -> (groundSpeed / 5.6f).coerceIn(0.5f, 1.5f)
                else -> 1f
            }
            // 步态切换相位延续 + 短过渡：crossfade 对相位不同步的全身步态是双片混播
            // （顿挫根源）——新片从旧片等相位处切入，过渡压到 150ms，混合期相位对齐即平滑。
            // 跌落(hold 定格)退出零过渡直切：跳跃动作的 ACTION 层持有末帧覆盖到落地，
            // 清层瞬间下层必须已就位——任何残留淡化窗口都会让收腿定格裸露
            //（落地后闪现即观感"跳跃播了一次半"）
            val prevIsFalling = locomotion.clip?.name == states.config("falling")?.animation
            // 过渡时长按配置走。原先硬钳 150ms 是相位发散时期的补丁——那会儿换片
            // 混合是两套步态各走各的速度，长过渡必然顿挫；carryPhase 对齐相位、
            // fade 侧跟随速度之后，混合期本身已经平滑，上限交回 clampBlend
            // （全局 1000ms 且不超过目标剪辑的 80%）
            setLayerTarget(
                locomotion, clip,
                if (prevIsFalling) 0f else config.transition * 0.05f,
                loops = config.playMode == PlayMode.LOOP, speed = speed,
                carryPhase = true
            )
        }

        // 首次渲染：各层直接落到目标权重。空层切入的淡入是为切换服务的，实体第一帧
        // 没有"上一个姿势"可淡——从零权重升上来的 0.25s 里模型是几何初始姿态按比例放大，
        // 特效模型的巨面片会从脚底"长"出来
        if (firstRender) {
            for (layer in layers) if (layer.clip != null) layer.envelope = layer.envelopeTarget
        }

        // --- seek：跳转 ACTION 层时间轴（秒）。暂停状态下同样生效（定格到新帧） ---
        AnimationControlStore.consumeSeek(uuid)?.let { seconds ->
            // 跳转的是播控所在的那一层：上身播控 seek 不该去改运动层上的跳跃
            val seekLayer = if (activeForced?.toCombatLayer == true) actionCombat else actionMotion
            seekLayer.clip?.let { clip ->
                seekLayer.time = if (seekLayer.loops && clip.length > 0f) {
                    ((seconds % clip.length) + clip.length) % clip.length
                } else {
                    seconds.coerceIn(0f, clip.length)
                }
            }
        }

        // --- 时间推进（含 fade 侧；倒放 speed<0 在 advance 内处理回绕/首帧钳制） ---
        if (!paused) {
            for (layer in layers) {
                layer.clip?.let { layer.time = advance(it, layer.time, dt * layer.speed, layer.loops) }
                // fade 侧按旧片自己的速度推进：步速调制下两片速度不同，
                // 统一用 1× 会让混合期相位发散（carryPhase 只对齐了切入那一瞬）
                layer.fadeClip?.let {
                    layer.fadeTime = advance(it, layer.fadeTime, dt * layer.fadeSpeed, layer.fadeLoops)
                }
                if (layer.fadeElapsed >= 0f && layer.fadeDuration > 0f) {
                    layer.fadeElapsed = min(layer.fadeElapsed + dt, layer.fadeDuration)
                }
                // 包络向目标推进
                if (layer.envelope != layer.envelopeTarget) {
                    if (layer.envelopeRate > 0f) {
                        val step = dt * layer.envelopeRate
                        layer.envelope = if (layer.envelope < layer.envelopeTarget) {
                            min(layer.envelope + step, layer.envelopeTarget)
                        } else {
                            max(layer.envelope - step, layer.envelopeTarget)
                        }
                    } else {
                        layer.envelope = layer.envelopeTarget
                    }
                }
                if (layer.envelope <= 0f && layer.envelopeTarget == 0f) {
                    layer.clip = null
                    layer.fadeClip = null
                    layer.fadeElapsed = -1f
                }
            }
        }

        // 死亡状态无条件钉死：DEATH 层时间钳制到剪辑末帧、清除该层一切交叉淡化。
        // 任何残留的 fadeClip 都会把"躺倒姿势"拉向"站立姿势"——视觉即"站起来"
        if (dead) {
            death.time = death.clip?.length ?: 0f
            death.fadeClip = null
            death.fadeElapsed = -1f
        }

        // --- 采样 + 层合成 ---
        // 表达式关键帧在此求值：query.* 来自 Lantern 构建的 per-entity queryValues，
        // variable.* 来自 packet 17 注入的 per-entity 变量（求值桥的 ThreadLocal 上下文）
        // 腿部归属：下层有实质腿部动作时（移动中、或跳跃等运动动作在播）才把腿让出去，
        // 原地出招保持动作完整。150ms 一阶平滑，起步停步不突变
        val motionLayerActive = actionMotion.clip != null && actionMotion.envelope > 0.01f
        val legTarget = if (movingCached || motionLayerActive) 1f else 0f
        legHandoff += (legTarget - legHandoff) * min(dt / 0.15f, 1f)

        val evalScope = buildEvalState(actor)
        evalScope.variables = MolangVariableStore.resolve(uuid, evalScope)
        pose = run {
            var composed: Map<String, FloatArray>? = null
            for (layer in layers) {
                val w = layerWeight(layer)
                if (w <= 0f) continue
                val sampled = sampleLayer(layer, evalScope) ?: continue
                composed = if (layer.kind == LayerKind.ACTION_COMBAT) {
                    blendCombat(composed ?: emptyMap(), sampled, w, legHandoff)
                } else when (layer.blend) {
                    BlendType.OVERRIDE -> blendPoses(composed ?: emptyMap(), sampled, w)
                    BlendType.ADDING -> addPoses(composed ?: emptyMap(), sampled, w)
                }
            }
            composed ?: emptyMap()
        }
    }

    /**
     * 层选片：换片时旧片进入淡出（过渡为 0 时立即切换，无淡化）；
     * 目标为空 = 层清空进入退出淡化（时长沿用最近一次进入过渡）。
     * 过渡时长钳制（smooth 手感规范）：上限 1000ms 且不超过目标剪辑时长的 80%。
     * [carryPhase]：循环步态间换片按相位映射起点（旧 time/旧长 → 新片等相位处），
     * 消除换片相位跳变（步态顿挫的根源之一）
     */
    private fun setLayerTarget(
        layer: LayerState,
        target: ClipData?,
        blendSeconds: Float,
        loops: Boolean,
        speed: Float,
        pinLastFrame: Boolean = false,
        carryPhase: Boolean = false,
        forceRestart: Boolean = false
    ) {
        // 清层：剪辑不立即丢弃，让包络降到 0——期间本层仍按末帧/循环采样，
        // 占比逐渐让位给下层。包络归零后才真正释放（时间推进段处理）
        if (target == null) {
            if (layer.clip != null && layer.envelopeTarget != 0f) {
                val clamped = clampBlend(blendSeconds, layer.clip)
                layer.envelopeTarget = 0f
                layer.envelopeRate = if (clamped > 0f) 1f / clamped else 0f
            }
            return
        }
        if (target === layer.clip && !forceRestart) {
            layer.speed = speed
            // 淡出途中被重新选中：掉头淡回满权重，不重启时间轴
            if (layer.envelopeTarget != 1f) {
                layer.envelopeTarget = 1f
                layer.envelopeRate = if (layer.blendSeconds > 0f) 1f / layer.blendSeconds else 0f
            }
            return
        }
        val prevClip = layer.clip
        val phase = if (carryPhase && loops && prevClip != null && layer.loops && prevClip.length > 0f) {
            (layer.time / prevClip.length).coerceIn(0f, 1f)
        } else 0f
        val clamped = clampBlend(blendSeconds, target)
        layer.blendSeconds = clamped
        if (layer.clip != null && clamped > 0f) {
            // 层内换片：旧片转入 fade 侧，按它自己的速度继续推进
            layer.fadeClip = layer.clip
            layer.fadeTime = layer.time
            layer.fadeSpeed = layer.speed
            layer.fadeElapsed = 0f
            layer.fadeDuration = clamped
            layer.fadeLoops = layer.loops
        } else {
            layer.fadeClip = null
            layer.fadeElapsed = -1f
        }
        // 空层切入（含淡出未尽时的重新占用）：包络从当前值升到 1，
        // 本层未控制的骨骼全程不受影响
        layer.envelopeTarget = 1f
        layer.envelopeRate = if (clamped > 0f) 1f / clamped else 0f
        if (clamped <= 0f) layer.envelope = 1f
        layer.clip = target
        // 真死亡后的任何切换钉死末帧：无论从哪条路径切入 death 剪辑，
        // 都不从第 0 帧重播（消除"站起来一下再死"）
        layer.time = when {
            pinLastFrame -> target.length
            carryPhase && loops && target.length > 0f -> phase * target.length
            else -> 0f
        }
        layer.loops = loops
        layer.speed = speed
    }

    /** 采样单层：主片与 fade 侧（层内换片的旧片）按过渡进度交叉淡化。
     *  层的整体进出不在这里处理——那是可见度包络的职责，作用在层合成权重上 */
    private fun sampleLayer(
        layer: LayerState,
        scope: MolangScope
    ): Map<String, FloatArray>? {
        // 该层的表达式关键帧用该层自己的时间轴求值
        (scope as? MutableMolangScope)?.setQuery(QueryNames.ANIM_TIME, layer.time.toDouble())
        val main = layer.clip?.let { samplePose(it, layer.time, layer.loops, scope) } ?: return null
        val fade = layer.fadeClip
        return if (fade != null && layer.fadeElapsed >= 0f && layer.fadeDuration > 0f) {
            blendPoses(
                samplePose(fade, layer.fadeTime, layer.fadeLoops, scope),
                main, layer.fadeElapsed / layer.fadeDuration, crossfade = true
            )
        } else {
            main
        }
    }

    /** 层合成权重 = 静态权重 × 可见度包络（空层切入 0->1、清层 1->0） */
    private fun layerWeight(layer: LayerState): Float =
        layer.weight * layer.envelope.coerceIn(0f, 1f)

    /**
     * 出招层合成：旋转按部位分权，位移与缩放整体跟随腿部归属。
     *
     * 位移不能分层——它表达的是整体重心。攻击资产靠「躯干与腿同幅下沉」表现蹲身发力
     * （body 与 rightLeg 的 pos.y 同为 -4.5），而 geo 里 body 与双腿是 waist 下的
     * 兄弟节点，body 下沉不会带动腿。若躯干取出招的位移、腿取下层的位移，
     * 躯干会整体下移而腿留在原位——躯干下端直接插进髋关节以下，即「严重的分离感」。
     * 所以位移一律按腿部归属系数取：原地出招整体下蹲完整，跑动中出招躯干只前倾不下沉。
     *
     * 旋转可以分层：body 与双腿的枢轴同在髋部，躯干绕髋前倾时连接点重合，不产生缝隙。
     */
    private fun blendCombat(
        base: Map<String, FloatArray>,
        layerPose: Map<String, FloatArray>,
        w: Float,
        handoff: Float
    ): Map<String, FloatArray> {
        val legW = w * (1f - handoff)
        val out = HashMap<String, FloatArray>(base.size + layerPose.size)
        out.putAll(base)
        for ((name, b) in layerPose) {
            val rotW = if (name in upperBodyBones) w else legW
            val a = out[name]
            out[name] = FloatArray(9) { i ->
                // 0..2 旋转按部位；3..5 位移、6..8 缩放一律跟随腿部归属（整体重心不可分层）
                val wi = if (i < 3) rotW else legW
                val av = a?.get(i)
                when {
                    av == null -> if (b[i].isNaN()) b[i] else b[i] * wi
                    wi <= 0f -> av
                    av.isNaN() -> b[i]
                    b[i].isNaN() -> av
                    i < 3 -> lerpAngle(av, b[i], wi)
                    else -> av + (b[i] - av) * wi
                }
            }
        }
        return out
    }

    /**
     * 构建 molang 求值上下文：query.* 只读 scope 的 queries 表（缺失返回 0）。
     * query 值由 Lantern 从实体快照与播放器运动实测换算，未提供的 query 返回 0
     */
    private fun buildEvalState(actor: ActorSnapshot): MutableMolangScope {
        val scope = MutableMolangScope()
        fun q(name: String, value: Double) {
            scope.setQuery(name, value)
        }

        // anim_time 先填一个兜底值，真正取值在 sampleLayer 里按层覆写
        q(QueryNames.ANIM_TIME, (layers.lastOrNull { it.clip != null }?.time ?: 0f).toDouble())
        evalQueries = scope
        q(QueryNames.IS_MOVING, if (movingCached) 1.0 else 0.0)
        q(QueryNames.GROUND_SPEED, groundSpeed.toDouble())
        q(QueryNames.VERTICAL_SPEED, verticalSpeed.toDouble())
        q(QueryNames.IS_ON_GROUND, if (airborne) 0.0 else 1.0)
        q(QueryNames.LIFE_TIME, actor.tickCount / 20.0)

        q(QueryNames.TIME_STAMP, actor.gameTime.toDouble())
        q(QueryNames.TIME_OF_DAY, actor.dayTime / 24000.0)
        q(QueryNames.DAY, actor.gameTime / 24000.0)

        if (actor.living) {
            q(QueryNames.IS_ALIVE, if (actor.alive) 1.0 else 0.0)
            q(QueryNames.HEALTH, actor.health.toDouble())
            q(QueryNames.MAX_HEALTH, actor.maxHealth.toDouble())
            q(QueryNames.HURT_TIME, actor.hurtTime.toDouble())
            q(QueryNames.IS_IN_WATER, if (actor.inWater) 1.0 else 0.0)
            q(QueryNames.IS_RIDING, if (actor.passenger) 1.0 else 0.0)
            q(QueryNames.IS_SNEAKING, if (actor.sneaking) 1.0 else 0.0)
            q(QueryNames.IS_SPRINTING, if (actor.sprinting) 1.0 else 0.0)
            q(QueryNames.IS_BABY, if (actor.baby) 1.0 else 0.0)
            q(QueryNames.SCALE, actor.scale.toDouble())
            q(QueryNames.HEAD_X_ROTATION, actor.pitch.toDouble())
            q(QueryNames.HEAD_Y_ROTATION, actor.yaw.toDouble())
            // 别名：GeckoLib 的标准名是 head_x_rotation / head_y_rotation，
            // 而不少既有资产惯用更短的 query.pitch / query.yaw。两套名字都认，
            // 同一份资产拿过来直接能跑，不必为了改名重导一遍动画
            q(QueryNames.PITCH, actor.pitch.toDouble())
            q(QueryNames.YAW, actor.yaw.toDouble())
            q(QueryNames.BODY_Y_ROTATION, actor.bodyYaw.toDouble())
            q(QueryNames.YAW_SPEED, actor.yawSpeed.toDouble())
            q(QueryNames.DEATH_TICKS, if (dead) death.time * 20.0 else 0.0)
        }
        return scope
    }

    /**
     * LOCOMOTION 层全身移动状态机（互斥切换，驻留防抖）：
     * 攀爬 > 游泳 > 飞行 > 骑乘 > 潜行(静止/移动) > 空中 > 疾跑 > walk/idle 兜底。
     * 全身动画必须放本层——若放 POSTURE 会与 LOCOMOTION 的 walk 双层同播，
     * 两套步态逐帧混合=部件分离、生硬卡脚（2026-09-04 真机实证）
     */
    private fun selectLocomotion(
        actor: ActorSnapshot,
        moving: Boolean,
        states: AnimationStateMapping,
        clips: Map<String, ClipData>
    ): Triple<ClipData, StateConfig, String>? {
        var candidate: Triple<ClipData, StateConfig, String>? = null

        fun pick(vararg keys: String) {
            if (candidate == null) candidate = resolveState(states, clips, *keys)
        }

        if (actor.climbing) pick("climbing")
        if (candidate == null && actor.inWater) {
            pick(if (moving) "swim_move" else "swim", "swim_walk", "swim_idle")
        }
        if (candidate == null && actor.flying) {
            pick(if (moving) "fly_move" else "fly_static")
        }
        if (candidate == null && actor.passenger) {
            pick(if (moving) "ride_move" else "ride", "riding_walk", "riding_idle")
        }
        if (candidate == null && actor.sneaking) {
            pick(if (moving) "sneak_walk" else "sneak_hold", "sneak_idle")
        }
        // falling 进入需 600ms 空中确认：普通跳跃/跑跳滞空约 0.53s 全程被 ACTION 跳跃
        // 覆盖，跌落定格与跳跃动画都收腿——任何裸露窗口都被读作"又播了半次"。
        // 短跳全程不进跌落；真高空下落 600ms 后正常进入
        val airConfirmed = airSinceMs != 0L && System.currentTimeMillis() - airSinceMs > 600
        if (candidate == null && airborne && airConfirmed) pick("falling")
        if (candidate == null && sprintActive && moving) pick("sprint")

        val resolved = candidate
            ?: resolveState(states, clips, if (moving) "walk" else "idle")
            ?: return null
        return applyDwell(resolved, states, clips, dwellKey = true)
    }

    /**
     * POSTURE 层上身姿态链：使用物品(分主副手) > 持物(hold_*)。未命中返回 null 清层
     */
    private fun selectUpperBodyPosture(
        actor: ActorSnapshot,
        states: AnimationStateMapping,
        clips: Map<String, ClipData>
    ): Triple<ClipData, StateConfig, String>? {
        var candidate: Triple<ClipData, StateConfig, String>? = null

        fun pick(vararg keys: String) {
            if (candidate == null) candidate = resolveState(states, clips, *keys)
        }

        if (actor.usingItem) {
            val mainHand = actor.usingMainHand
            when (actor.useAction) {
                UseActionKind.BOW, UseActionKind.CROSSBOW -> pick(
                    if (mainHand) "use_bow_main" else "use_bow_off", "pull_bow"
                )
                UseActionKind.BLOCK -> pick(
                    if (mainHand) "use_shield_main" else "use_shield_off"
                )
                UseActionKind.EAT, UseActionKind.DRINK -> pick("use_eat", "eat", "drink")
                else -> {}
            }
        } else {
            actor.mainHandHold?.let { type ->
                pick("hold_$type")
            }
        }

        val resolved = candidate ?: return null
        return applyDwell(resolved, states, clips, dwellKey = false)
    }

    /**
     * 驻留防抖（「别打断当前状态」条件的内核化）：候选状态 ≠ 当前状态且
     * 当前驻留不满 POSTURE_DWELL_MS 时保持当前状态——判定边界抖动不再让过渡动画
     * 反复重播；旧状态无剪辑时穿透，不因防抖卡死。
     * [dwellKey] true=LOCOMOTION 组 / false=POSTURE 组，两组独立计时
     */
    private fun applyDwell(
        resolved: Triple<ClipData, StateConfig, String>,
        states: AnimationStateMapping,
        clips: Map<String, ClipData>,
        dwellKey: Boolean
    ): Triple<ClipData, StateConfig, String> {
        val (clip, config, newKey) = resolved
        val now = System.currentTimeMillis()
        val current = if (dwellKey) locoState else postureState
        if (newKey != current && current != null && now - (if (dwellKey) locoSinceMs else postureSinceMs) < POSTURE_DWELL_MS) {
            val keep = resolveState(states, clips, current)
            if (keep != null) return keep
        }
        if (dwellKey) {
            locoState = newKey
            locoSinceMs = now
        } else {
            postureState = newKey
            postureSinceMs = now
        }
        return Triple(clip, config, newKey)
    }

    /** 按优先序解析状态键：第一个「配了且剪辑存在」的键胜出 */
    private fun resolveState(
        states: AnimationStateMapping,
        clips: Map<String, ClipData>,
        vararg keys: String
    ): Triple<ClipData, StateConfig, String>? {
        for (key in keys) {
            val config = states.config(key) ?: continue
            val clip = clips[config.animation] ?: continue
            return Triple(clip, config, key)
        }
        return null
    }

    /**
     * 连招段位推进。
     *
     * 两个正交的窗口（对标系统两家的语义并存）：
     * - cancelAt：当前段播到该进度即可被下一段接替，不必等整段播完——连招不顿挫的来源；
     * - window：距上次出手超过该时长即视为一套连招结束，下次回到第一段。
     *
     * @return 是否真的接上了下一段（没接上则由调用方排队）
     */
    private fun advanceCombo(): Boolean {
        val combo = currentCombo() ?: return false
        val steps = combo.steps
        if (steps.isEmpty()) return false
        // 换了连招组（动画组切换）：段位归零，否则会拿新组的招式接着旧组的段号
        if (comboSetId != combo.id) {
            comboSetId = combo.id
            comboIndex = -1
        }
        val now = System.currentTimeMillis()
        val slot = if (combo.toCombatLayer) combatSlot else motionSlot
        val layer = if (combo.toCombatLayer) actionCombat else actionMotion

        // 当前段还没到取消点：不接，交给排队
        val current = steps.getOrNull(comboIndex)
        if (slot.clip != null && current != null) {
            val playing = layer.clip
            if (playing != null && playing.length > 0f &&
                layer.time < playing.length * current.cancelAt
            ) {
                return false
            }
        }

        // 超出输入窗口即回到第一段。窗口从「当前段可取消」那一刻起算：
        // 从起播起算的话，左砍 0.75s×0.7=525ms 才能接、窗口 700ms 就只剩 175ms 可接，
        // 蓄力段取消点 1.2s 更是直接落在 800ms 窗口之外——连招看起来是"断了"
        val window = current?.windowMs ?: 0L
        val windowStart = max(comboCancelReadyMs, comboLastTriggerMs)
        val index = if (comboIndex < 0 || (window > 0L && now - windowStart > window)) {
            0
        } else {
            (comboIndex + 1) % steps.size
        }
        val step = steps[index]
        val clip = clipLibrary(combo.file)?.get(step.animation) ?: return false
        val started = occupy(
            slot, clip,
            step.transitionTicks * 0.05f,
            loops = false,
            speed = 1f,
            uninterruptible = combo.uninterruptible,
            exitSeconds = step.exitTicks * 0.05f,
            exclusive = false
        )
        if (!started) return false
        comboIndex = index
        comboLastTriggerMs = now
        comboCancelReadyMs = now + (clip.length * step.cancelAt * 1000f).toLong()
        return true
    }

    /**
     * 释放运动槽。
     *
     * exitSeconds 不在这里复位：清层分支（when 的 else）正是在释放之后才读它来决定
     * 淡出时长，提前清成缺省值等于把配置的收尾过渡吃掉，动作播完直接硬切。
     * 下一个占用者由 occupy 覆写，播控由归层时显式赋值，都不会读到残留
     */
    private fun releaseMotionSlot() {
        motionSlot.clip = null
        motionSlot.uninterruptible = false
        motionSlot.exclusive = false
    }

    /** 触发运动动作（jump/sprint_jump/landing/spawn）；剪辑缺失不占槽位，判定链继续穿透 */
    private fun startMotionAction(config: StateConfig, clips: Map<String, ClipData>): Boolean =
        occupy(motionSlot, config, clips)

    /** 触发出招动作（attack_*）；与运动槽独立，起跳期间照常可出招 */
    private fun startCombatAction(config: StateConfig, clips: Map<String, ClipData>): Boolean =
        occupy(combatSlot, config, clips)

    /**
     * 起播下一刀并交替左右。
     *
     * 左右在**真正起播的瞬间**才决定，而不是在挥击边沿：单格输入队列会被后来的边沿
     * 覆盖，若边沿处就把左右定死，疯狂点击时落到哪一边全看点击时序，表现为连续几次
     * 同一边。起播时决定则播放序列严格交替，与点击快慢无关。
     * 只有成功占用槽位才翻转，否则状态键缺失会白白吃掉一次交替。
     */
    private fun tryStartAttack(states: AnimationStateMapping, clips: Map<String, ClipData>): Boolean {
        val key = if (!attackToggle) "attack_right" else "attack_left"
        val entry = resolveState(states, clips, key) ?: return false
        if (!startCombatAction(entry.second, clips)) return false
        attackToggle = !attackToggle
        return true
    }

    private fun occupy(slot: ActionSlot, config: StateConfig, clips: Map<String, ClipData>): Boolean {
        val clip = clips[config.animation] ?: return false
        return occupy(slot, clip, config.transition * 0.05f, config.playMode == PlayMode.LOOP, 1f, false, 0.05f, false)
    }

    /** 占用动作槽（状态表触发与外部注入共用）；霸体动作播放期间拒绝被顶替 */
    private fun occupy(
        slot: ActionSlot,
        clip: ClipData,
        transitionSeconds: Float,
        loops: Boolean,
        speed: Float,
        uninterruptible: Boolean,
        exitSeconds: Float,
        exclusive: Boolean
    ): Boolean {
        if (slot.clip != null && slot.uninterruptible) return false
        // 播控占着这一层就不接本地动作：接了也画不出来，冷却、位移、无敌帧却全会发生，
        // 播控一结束还会从第 0 帧补播。全身播控同时封住出招层（技能演出即独占）
        if (slot === motionSlot && forcedOwnsMotion) return false
        if (slot === combatSlot && (forcedOwnsCombat || forcedOwnsMotion)) return false
        // 出招撞上独占中的全身动作：直接放弃，也不排队（翻滚完不该补播一刀）
        if (slot === combatSlot && motionSlot.clip != null && motionSlot.exclusive) return false
        val lengthMs = (clip.length * 1000f).toLong()
        slot.clip = clip
        slot.transitionSeconds = transitionSeconds
        slot.loops = loops
        slot.speed = speed
        slot.exclusive = exclusive
        slot.uninterruptible = uninterruptible
        slot.exitSeconds = exitSeconds
        slot.restart = true
        // 独占动作起播即清掉正在进行的出招，避免上半身残留半截挥砍
        if (slot === motionSlot && exclusive) {
            combatSlot.clip = null
            combatSlot.uninterruptible = false
            queuedAttack = false
        }
        slot.deadline = System.currentTimeMillis() +
            (if (lengthMs > 0) lengthMs + 300L else LOCAL_ACTION_FALLBACK_MS)
        return true
    }

    /**
     * 外部注入一次性动作（按键触发的翻滚等）。剪辑由调用方从独立动画库取好，
     * 不经状态表，因此与 costume 绑定的动画文件解耦。
     * @param toCombatLayer true=上身出招层，false=全身运动层
     */
    fun triggerAction(
        clip: ClipData,
        transitionSeconds: Float,
        exitSeconds: Float,
        speed: Float,
        uninterruptible: Boolean,
        exclusive: Boolean,
        toCombatLayer: Boolean
    ): Boolean {
        // 按键触发发生在渲染帧之间，占层标记可能还是上一帧的；直接查存储保证当帧准确
        val forced = AnimationControlStore.get(uuid)
        if (forced != null && (!forced.toCombatLayer || toCombatLayer)) return false
        return occupy(
            if (toCombatLayer) combatSlot else motionSlot,
            clip, transitionSeconds, false, speed, uninterruptible, exitSeconds, exclusive
        )
    }

    /**
     * 位移实测的运动检测（水平移动 + 垂直速度），60ms 采样窗口 + 滞回，
     * 同时维护空中状态与 jump/landing 边沿。返回当前移动判定
     */
    private fun computeMotion(x: Double, y: Double, z: Double, physicallyGrounded: Boolean): Boolean {
        val now = System.currentTimeMillis()
        // 物理落地信号先于采样窗口处理：连跳（按住空格）的触地窗口短于 45ms 采样间隔，
        // 纯位移判定会整个错过，airborne 一直挂着 -> 第二跳的 jumpEdge 因 !airborne
        // 前置条件不成立而永不触发。onGround 本地玩家每 tick 更新、远程由移动包同步，
        // 作为「退出空中」的信号宁可早不可晚
        if (physicallyGrounded && airborne && everFell) {
            landingEdge = true
            airborne = false
            everFell = false
            airSinceMs = 0L
            wasRising = false
        }
        val last = lastPos
        if (last == null) {
            lastPos = doubleArrayOf(x, y, z)
            lastPosMs = now
            return movingCached
        }
        val elapsed = now - lastPosMs
        // 45ms：窗口越小触发越快，但本地玩家位置预测修正的毛刺会被放大成
        // 垂直速度假边沿（25ms 实测出现落地前二次起跳）；45ms 是延迟与噪声的折中
        if (elapsed < 45) return movingCached

        val dx = x - last[0]
        val dy = y - last[1]
        val dz = z - last[2]
        if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQ) {
            lastPos = doubleArrayOf(x, y, z)
            lastPosMs = now
            airborne = false
            wasRising = false
            return movingCached
        }
        val secs = elapsed / 1000.0
        val speedSq = (dx * dx + dz * dz) / (secs * secs)
        movingCached = if (movingCached) speedSq > 0.04 else speedSq > 0.09
        groundSpeed = kotlin.math.sqrt(speedSq).toFloat()
        verticalSpeed = (dy / secs).toFloat()

        val vy = verticalSpeed.toDouble()
        val rising = vy > RISE_VY
        val fallingNow = vy < FALL_VY
        val grounded = abs(vy) < GROUNDED_VY
        // 落地边沿必须"下落过"才成立：跳跃顶点垂直速度短暂归零会被 grounded 判定
        // 误读为落地（曾把跳跃动画顶点快进到末帧一帧闪没）
        if (airborne && grounded && !rising && !fallingNow && everFell) landingEdge = true
        // 起跳边沿只在地面成立：空中（顶点失速/落地前的位置修正毛刺）不再触发——
        // 否则一次物理跳跃会被垂直速度毛刺二次触发（"跳跃播两次"的根因）
        if (rising && !wasRising && !airborne) jumpEdge = true
        if (fallingNow) everFell = true
        // 空中滞回：起跳/下落进入，真落地（接地且已下落过）退出——顶点失速不退出
        val wasAirborne = airborne
        airborne = rising || fallingNow || (airborne && !(grounded && everFell))
        if (!airborne) {
            everFell = false
            airSinceMs = 0L
        } else if (!wasAirborne) {
            airSinceMs = now
        }
        wasRising = rising
        lastPos = doubleArrayOf(x, y, z)
        lastPosMs = now
        return movingCached
    }
}

/** 过渡时长手感规范（smooth 参数）的上限：1000ms */
private const val MAX_BLEND_SECONDS = 1f

/** 过渡时长手感规范（smooth 参数）：上限 1000ms；不超过目标剪辑时长的 80%（剪辑长度已知时） */
private fun clampBlend(seconds: Float, target: ClipData?): Float {
    var v = seconds.coerceIn(0f, MAX_BLEND_SECONDS)
    if (target != null && target.length > 0f) {
        v = min(v, target.length * 0.8f)
    }
    return v
}

/**
 * 按剪辑播放语义推进时间（回环/钳末帧由播放器侧配置决定，而非剪辑 json 的 loop 字段）。
 * 负速度 = 倒放：LOOP 向后回绕到尾部；ONCE/钳末帧语义钳在首帧
 */
private fun advance(clip: ClipData, time: Float, dt: Float, loops: Boolean): Float {
    val next = time + dt
    return if (loops && clip.length > 0f) {
        ((next % clip.length) + clip.length) % clip.length
    } else {
        next.coerceIn(0f, clip.length)
    }
}

/** 采样剪辑 -> 骨骼写入空间姿势（轴变换在此完成；缺失轨道记 NaN = 该轴回落静态值）。
 *  关键帧值在此按 [scope] 求值（数值关键帧是常量，求值即字段读） */
private fun samplePose(
    clip: ClipData,
    rawTime: Float,
    loops: Boolean,
    scope: MolangScope
): Map<String, FloatArray> {
    val time = when {
        loops && clip.length > 0f -> rawTime % clip.length
        else -> min(max(rawTime, 0f), clip.length)
    }
    val result = HashMap<String, FloatArray>(clip.bones.size)
    for ((boneName, tracks) in clip.bones) {
        val rot = sampleTrack(tracks.rotation, time, scope)
        val pos = sampleTrack(tracks.position, time, scope)
        val scale = sampleTrack(tracks.scale, time, scope)
        result[boneName] = floatArrayOf(
            Math.toRadians((-(rot?.x ?: 0f)).toDouble()).toFloat(),
            Math.toRadians((-(rot?.y ?: 0f)).toDouble()).toFloat(),
            Math.toRadians((rot?.z ?: 0f).toDouble()).toFloat(),
            pos?.x ?: Float.NaN,
            pos?.y ?: Float.NaN,
            pos?.z ?: Float.NaN,
            scale?.x ?: Float.NaN,
            scale?.y ?: Float.NaN,
            scale?.z ?: Float.NaN
        )
    }
    return result
}

    /** 混合两姿势。
     *  [crossfade]=true（层内换片/空层切入淡入）：双方独有骨骼都随 w 过渡——
     *  旧片独有淡出、新片独有淡入，消除淡出末帧独占骨骼从满值到消失的跳变；
     *  false（层间 OVERRIDE）：上层独有骨骼随层权重渐现/渐隐，下层独有保持满值
     *  （上层淡入期不该压暗下层未被覆盖的骨骼）。NaN 轴保持穿透语义 */
private fun blendPoses(
    from: Map<String, FloatArray>,
    to: Map<String, FloatArray>,
    w: Float,
    crossfade: Boolean = false
): Map<String, FloatArray> {
    val out = HashMap<String, FloatArray>(from.size + to.size)
    for ((name, a) in from) {
        val b = to[name]
        out[name] = if (b != null) {
            FloatArray(9) { i ->
                when {
                    a[i].isNaN() -> b[i]
                    b[i].isNaN() -> a[i]
                    // 0..2 是旋转：必须按最短弧插值，见 lerpAngle
                    i < 3 -> lerpAngle(a[i], b[i], w)
                    else -> a[i] + (b[i] - a[i]) * w
                }
            }
        } else if (crossfade) {
            scaleAxes(a, 1f - w)
        } else {
            a
        }
    }
    for ((name, b) in to) {
        if (name !in out) out[name] = scaleAxes(b, w)
    }
    return out
}

    /**
     * 角度插值，走最短弧。
     *
     * 姿势里的旋转是连续累积的欧拉角，不取模——动画内部要靠它表达「转一整圈」
     * （翻滚资产的 body 从 22° 连续转到 382°，几何上回到原位但数值差 360）。
     * 混合时若直接线性插值，382° 融向下层的 20° 会沿着数值方向倒退一整圈，
     * 在过渡时长内反向翻转，观感就是动作播完突然「重置」。
     * 把角度差归一到 [-PI, PI] 即走真实的最短路径；差值本就在该区间内的
     * 普通动画完全不受影响。
     */
private fun lerpAngle(from: Float, to: Float, w: Float): Float {
    val twoPi = (Math.PI * 2.0).toFloat()
    var delta = to - from
    if (delta > Math.PI || delta < -Math.PI) {
        delta -= Math.floor((delta + Math.PI) / twoPi.toDouble()).toFloat() * twoPi
    }
    return from + delta * w
}

/** 按系数缩放姿势各轴（NaN 轴保持 NaN = 未控轴继续穿透回落静态值） */
private fun scaleAxes(v: FloatArray, k: Float): FloatArray =
    FloatArray(9) { i -> if (v[i].isNaN()) v[i] else v[i] * k }

/**
 * ADDING 合成：叠加层姿态按权重逐轴加到基线上（欧拉逐轴相加，与偏移语义一致，
 * 无万向节问题）。叠加层 NaN 轴（未控轴）不动基线；基线 NaN 轴取叠加值。
 */
private fun addPoses(base: Map<String, FloatArray>, addend: Map<String, FloatArray>, w: Float): Map<String, FloatArray> {
    val out = HashMap<String, FloatArray>(base.size + addend.size)
    for ((name, a) in base) {
        val b = addend[name]
        out[name] = if (b != null) {
            FloatArray(9) { i ->
                when {
                    a[i].isNaN() -> b[i]
                    b[i].isNaN() -> a[i]
                    else -> a[i] + b[i] * w
                }
            }
        } else {
            a
        }
    }
    for ((name, b) in addend) {
        // 基线没有该骨骼时同样要按权重缩放：直接取满值会让叠加层在淡入期就整幅生效
        if (name !in out) out[name] = scaleAxes(b, w)
    }
    return out
}

private fun sampleTrack(frames: List<Keyframe>, time: Float, scope: MolangScope): Vec3? {
    if (frames.isEmpty()) return null
    if (frames.size == 1) return frames[0].value.eval(scope)
    if (time <= frames.first().time) return frames.first().value.eval(scope)
    if (time >= frames.last().time) return frames.last().value.eval(scope)

    var i = 0
    while (i < frames.size - 1 && frames[i + 1].time <= time) i++
    val a = frames[i]
    val b = frames[i + 1]
    val span = b.time - a.time
    if (span <= 0f) return b.value.eval(scope)
    val t = (time - a.time) / span

    // 区间端点：prev.post -> next.pre；catmullrom 控制点取 pre 值
    //（区间端点语义实证：start=prev.post, end=next.pre）
    val start = a.value.eval(scope)
    val end = (b.pre ?: b.value).eval(scope)

    val catmull = a.lerpMode == "catmullrom" || b.lerpMode == "catmullrom"
    if (!catmull) {
        return Vec3(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        )
    }
    val p0 = frames[max(i - 1, 0)].let { (it.pre ?: it.value).eval(scope) }
    val p3 = frames[min(i + 2, frames.size - 1)].let { (it.pre ?: it.value).eval(scope) }
    return Vec3(
        spline(t, p0.x, start.x, end.x, p3.x),
        spline(t, p0.y, start.y, end.y, p3.y),
        spline(t, p0.z, start.z, end.z, p3.z)
    )
}

/** Catmull-Rom 样条（与 GeckoLib getPointOnSpline 同式） */
private fun spline(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
        2f * p1 +
            (p2 - p0) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        )
}
