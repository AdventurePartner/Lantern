package org.lantern.animation

import java.util.UUID
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.HoeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.MaceItem
import net.minecraft.world.item.ShieldItem
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.item.TridentItem
import org.lantern.model.renderstate.AnimationControlStore
import org.lantern.model.wrapper.AnimationStateMapping
import org.lantern.model.wrapper.PlayMode
import org.lantern.model.wrapper.StateConfig
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.processing.AnimationState
import software.bernie.geckolib.loading.math.MathParser
import software.bernie.geckolib.loading.math.MolangQueries
import software.bernie.geckolib.loading.math.value.Variable
import software.bernie.geckolib.animatable.processing.AnimationProcessor
import software.bernie.geckolib.cache.`object`.GeoBone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lantern 自托管动画播放器（每实体一个）。
 *
 * 层栈模型（P0，取代单剪辑判定链）：
 * 四层自下而上合成 LOCOMOTION(walk/idle) < POSTURE(姿态链) < ACTION(播控/一次性) < DEATH，
 * 每层单动画槽位（换片顶旧），层内交叉淡化沿用单剪辑时代的 fade 机制，
 * 层清空即进入退出淡化（末段姿态按最近一次进入过渡时长衰减露出下层）。
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
 */
class AnimationPlayer(private val uuid: UUID) {

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
        // 姿态状态最小驻留（龙核"别打断当前"防抖的内核化）：判定边界抖动（潜行微移的
        // moving 边界、水边、空中边沿）会让 POSTURE 层换片再换回，进入型过渡动画
        // （蹲下过程 hold）每次被重新选中都从头播——驻留期内保持当前状态不切换
        const val POSTURE_DWELL_MS = 150L
    }

    /** 层种类，ordinal 即合成顺序（低 -> 高） */
    private enum class LayerKind { LOCOMOTION, POSTURE, ACTION, DEATH }

    /** 层间混合模式：OVERRIDE 按控制集覆盖（未控骨骼穿透）；ADDING 数值逐轴叠加 */
    private enum class BlendType { OVERRIDE, ADDING }

    /**
     * 单层状态：结构上是单剪辑时代的字段组搬运（clip/time/loops + fade 组），
     * 加上层属性（blend/weight）与退出淡化标记。
     * blendSeconds 记录最近一次进入过渡，层清空时作为退出淡化时长。
     */
    private class LayerState(val kind: LayerKind) {
        var clip: ClipData? = null
        var time = 0f
        var speed = 1f
        var loops = true

        var blend: BlendType = BlendType.OVERRIDE
        var weight = 1f
        var blendSeconds = 0.25f

        // 层内交叉淡化：换片时旧片继续推进并在过渡时长内淡出；
        // 退出淡化是它的特例——clip 已清空、只剩 fade 侧按权重衰减。
        // fadeFromPose：空层切入时的淡入起点（上一帧全合成姿态快照）——
        // 攻击/跳跃/拉弓从空层切入原本是零过渡硬切
        var fadeClip: ClipData? = null
        var fadeTime = 0f
        var fadeElapsed = -1f
        var fadeDuration = 0.25f
        var fadeLoops = true
        var fadeFromPose: Map<String, FloatArray>? = null
        var exiting = false
    }

    private val layers = LayerKind.entries.map { LayerState(it) }
    private val locomotion get() = layers[LayerKind.LOCOMOTION.ordinal]
    private val posture get() = layers[LayerKind.POSTURE.ordinal]
    private val action get() = layers[LayerKind.ACTION.ordinal]
    private val death get() = layers[LayerKind.DEATH.ordinal]

    private var channelId = -1L
    private var lastFinishReportedId = -1L
    private var dead = false
    private var lastNanos = 0L

    // 本地一次性动作（jump/landing/spawn）：边沿触发，播完回落；被播控覆盖时由截止时间兜底释放
    private var localAction: StateConfig? = null
    private var localActionDeadline = 0L

    var pose: Map<String, FloatArray> = emptyMap()
        private set

    /** 上一帧的全合成姿态：空层切入（攻击/跳跃/拉弓）时作为淡入起点 */
    private var lastComposedPose: Map<String, FloatArray> = emptyMap()

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
    private var queuedAttack: String? = null
    private var queuedAttackExpireMs = 0L

    fun drive(
        clips: Map<String, ClipData>,
        forced: AnimationControlStore.ForcedAnimation?,
        entity: Entity,
        states: AnimationStateMapping,
        firstRender: Boolean
    ) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1_000_000_000f, 0.25f)
        lastNanos = now
        val living = entity as? LivingEntity
        val isMoving = computeMotion(entity.x, entity.y, entity.z)
        // 攀爬中位移实测的垂直速度（约 2.36 格/秒）会越过起跳/跌落阈值——
        // 梯子/藤蔓上强制清空中状态并拦截边沿，否则爬梯被演成连续跳跃
        val onClimb = living?.onClimbable() == true
        if (onClimb) {
            airborne = false
            wasRising = false
        }

        // --- 死亡状态（进入时锁定，停末帧） ---
        if (living != null && living.isDeadOrDying) {
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
        if (!paused && forced != null && !forced.loop) {
            val forcedClip = clips[forced.animation]
            val timelineEnded = forcedClip != null && forcedClip.length > 0f &&
                channelId == forced.id && action.clip === forcedClip && action.time >= forcedClip.length
            val timestampExpired = forced.expiresAtMs > 0 &&
                System.currentTimeMillis() >= forced.expiresAtMs
            if (timelineEnded || timestampExpired) {
                if (lastFinishReportedId != forced.id) {
                    lastFinishReportedId = forced.id
                    org.lantern.internal.network.NetworkParser.animationEventSender
                        ?.invoke(uuid, forced.animation, "finish")
                }
                if (forced.animation != states.death) {
                    AnimationControlStore.stop(uuid, forced.animation)
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
        localAction?.let { act ->
            val clip = clips[act.animation]
            val timelineEnded = clip != null && clip.length > 0f &&
                action.clip === clip && action.time >= clip.length
            if (clip == null || (timelineEnded && !airborne) || System.currentTimeMillis() >= localActionDeadline) {
                localAction = null
            }
        }

        // --- 排队攻击消费：当前刀进度达 70% 即可接刀（收势段可取消——combo
        //     cancel_at 语义；主砍段始终完整）。不排队则冷却一到就打断换片，
        //     每刀腰斩成快进；等播完则节奏偏慢（这批砍击不是蓄力重型） ---
        queuedAttack?.let { key ->
            val expired = System.currentTimeMillis() >= queuedAttackExpireMs
            val curClip = action.clip
            val isAttackClip = curClip != null &&
                (curClip.name == states.config("attack_left")?.animation ||
                    curClip.name == states.config("attack_right")?.animation)
            val canChain = localAction == null ||
                (isAttackClip && curClip != null && action.time >= curClip.length * 0.7f)
            if (canChain) {
                resolveState(states, clips, key)?.let { startLocalAction(it.second, clips) }
                queuedAttack = null
            } else if (expired) {
                queuedAttack = null
            }
        }

        // --- 边沿触发：spawn（实体生命周期内首次渲染）/ jump（起跳）/ landing（落地）。
        //     jump/landing 排除水中与骑乘：游泳上浮、载具颠簸同样产生垂直速度边沿，
        //     且 ACTION 层高于姿态链，swim/riding 姿态拦不住误触发 ---
        if (firstRender) {
            states.config("spawn")?.let { startLocalAction(it, clips) }
        }
        val edgeAllowed = living != null && !living.isInWater && !living.isPassenger && !onClimb
        if (jumpEdge) {
            jumpEdge = false
            if (edgeAllowed && living != null) {
                // 疾跑中起跳优先 sprint_jump（龙核 sprint-jump 触发器），回退 jump
                val jumpConfig = (if (living.isSprinting) {
                    resolveState(states, clips, "sprint_jump", "jump")
                } else {
                    resolveState(states, clips, "jump")
                })?.second
                jumpConfig?.let { startLocalAction(it, clips) }
            }
        }
        if (landingEdge) {
            landingEdge = false
            if (edgeAllowed) {
                states.config("landing")?.let { startLocalAction(it, clips) }
                // 物理落地即收势：跳跃类一次性动作的"展体落地"尾段快进掉——
                // 起跳触发有采样延迟，尾段约 0.15s 会拖到物理落地之后在地面播放，
                // 与空中段劈成两截（"起跳播一次、落地又一次"的观感来源）
                val la = localAction
                if (la != null) {
                    val isJumpAnim = la.animation == states.config("jump")?.animation ||
                        la.animation == states.config("sprint_jump")?.animation
                    if (isJumpAnim) {
                        action.clip?.let { c ->
                            if (c.length > 0.08f) action.time = max(action.time, c.length - 0.08f)
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
        val swinging = living?.swinging == true
        val attackAnimNow = living?.attackAnim ?: 0f
        val swingEdge = !wasSwinging && swinging
        val swingRestart = swinging && attackAnimNow < prevAttackAnim - 0.4f
        if (swingEdge || swingRestart) {
            attackToggle = !attackToggle
            val key = if (attackToggle) "attack_right" else "attack_left"
            // 当前刀未播完时排队而非打断——冷却节奏(0.625s)快于刀长(0.75s)，
            // 直接换片会把每刀腰斩成"快进连播"
            val attackClip = action.clip
            val attackInProgress = localAction != null && attackClip != null &&
                (attackClip.name == states.config("attack_left")?.animation ||
                    attackClip.name == states.config("attack_right")?.animation) &&
                action.time < attackClip.length
            if (attackInProgress) {
                queuedAttack = key
                queuedAttackExpireMs = System.currentTimeMillis() + 500L
            } else {
                resolveState(states, clips, key)?.let { startLocalAction(it.second, clips) }
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

        // ACTION 层：播控 > 本地一次性动作；都无时清层
        when {
            activeForced != null -> {
                // 同名动画再次下发（新 id）= 重放：重置时间轴从头播，
                // 否则 target===clip 早退会让第二次播放被顶旧片且立即误报 finish
                val restart = activeForced.id != channelId
                if (restart) channelId = activeForced.id
                setLayerTarget(
                    action, clips[activeForced.animation],
                    // 播控切入的过渡时长由服务端指令指定（tick × 50ms）；0 = 立即切换（如死亡）。
                    // 协议默认 5 tick = 0.25s，与行走层淡化时长一致，技能动画观感不变
                    activeForced.transition * 0.05f,
                    loops = activeForced.loop, speed = activeForced.speed,
                    forceRestart = restart
                )
            }
            localAction != null -> {
                val act = localAction!!
                setLayerTarget(
                    action, clips[act.animation],
                    act.transition * 0.05f,
                    loops = act.playMode == PlayMode.LOOP, speed = 1f
                )
            }
            // 清层淡出压到 50ms：一次性动作（跳跃等）末帧≈站立，快速让位；
            // 长淡出会在落地后与下层"跌落"定格姿态叠加约半秒——观感即"落地又播一遍"
            else -> setLayerTarget(action, null, min(action.blendSeconds, 0.05f), loops = false, speed = 1f)
        }

        // POSTURE 层：上身姿态链（use 弓/盾/食、hold_* 持物——窄控制面，
        // 与 LOCOMOTION 的移动状态并存：走路拉弓=腿走路+手臂拉弓）
        val posturePick = living?.let { selectUpperBodyPosture(it, states, clips) }
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
        if (living != null && living.isSprinting && isMoving) {
            sprintMs += dt
            if (sprintMs >= 0.15f) sprintActive = true
        } else {
            sprintMs = 0f
            sprintActive = false
        }
        val locomotionPick = living?.let { selectLocomotion(it, isMoving, states, clips) }
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
            setLayerTarget(
                locomotion, clip,
                if (prevIsFalling) 0f else min(config.transition * 0.05f, 0.15f),
                loops = config.playMode == PlayMode.LOOP, speed = speed,
                carryPhase = true
            )
        }

        // --- seek：跳转 ACTION 层时间轴（秒）。暂停状态下同样生效（定格到新帧） ---
        AnimationControlStore.consumeSeek(uuid)?.let { seconds ->
            action.clip?.let { clip ->
                action.time = if (action.loops && clip.length > 0f) {
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
                layer.fadeClip?.let { layer.fadeTime = advance(it, layer.fadeTime, dt, layer.fadeLoops) }
                if (layer.fadeElapsed >= 0f) {
                    layer.fadeElapsed += dt
                    if (layer.fadeElapsed >= layer.fadeDuration) {
                        layer.fadeClip = null
                        layer.fadeFromPose = null
                        layer.fadeElapsed = -1f
                        layer.exiting = false
                    }
                }
            }
        }

        // 死亡状态无条件钉死：DEATH 层时间钳制到剪辑末帧、清除该层一切交叉淡化。
        // 任何残留的 fadeClip 都会把"躺倒姿势"拉向"站立姿势"——视觉即"站起来"
        if (dead) {
            death.time = death.clip?.length ?: 0f
            death.fadeClip = null
            death.fadeElapsed = -1f
            death.exiting = false
        }

        // --- 采样 + 层合成 ---
        // 表达式关键帧在此求值：query.* 来自 Lantern 构建的 per-entity queryValues，
        // variable.* 来自 packet 17 注入的 per-entity 变量（ThreadLocal 上下文）
        val evalState = buildEvalState(entity, living)
        val vars = MolangVariableStore.resolve(uuid, evalState)
        pose = MolangContext.evaluate(vars) {
            var composed: Map<String, FloatArray>? = null
            for (layer in layers) {
                val layerPose = sampleLayer(layer, evalState) ?: continue
                val w = layerWeight(layer)
                composed = when (layer.blend) {
                    BlendType.OVERRIDE -> blendPoses(composed ?: emptyMap(), layerPose, w)
                    BlendType.ADDING -> addPoses(composed ?: emptyMap(), layerPose, w)
                }
            }
            composed ?: emptyMap()
        }
        lastComposedPose = pose
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
        if (target === layer.clip && !forceRestart) {
            layer.speed = speed
            return
        }
        val prevClip = layer.clip
        val phase = if (carryPhase && loops && prevClip != null && layer.loops && prevClip.length > 0f) {
            (layer.time / prevClip.length).coerceIn(0f, 1f)
        } else 0f
        val clamped = clampBlend(blendSeconds, target)
        layer.blendSeconds = clamped
        if (layer.clip != null && clamped > 0f) {
            layer.fadeClip = layer.clip
            layer.fadeTime = layer.time
            layer.fadeElapsed = 0f
            layer.fadeDuration = clamped
            layer.fadeLoops = layer.loops
            layer.exiting = target == null
        } else if (layer.clip == null && target != null && clamped > 0f) {
            // 空层切入：以上一帧全合成姿态为淡入起点（姿态连续，不再是硬切）
            layer.fadeFromPose = lastComposedPose
            layer.fadeElapsed = 0f
            layer.fadeDuration = clamped
            layer.exiting = false
        } else {
            layer.fadeClip = null
            layer.fadeElapsed = -1f
            layer.exiting = false
        }
        layer.clip = target
        // 真死亡后的任何切换钉死末帧：无论从哪条路径切入 death 剪辑，
        // 都不从第 0 帧重播（消除"站起来一下再死"）
        layer.time = when {
            pinLastFrame && target != null -> target.length
            carryPhase && target != null && loops && target.length > 0f -> phase * target.length
            else -> 0f
        }
        layer.loops = loops
        layer.speed = speed
    }

    /** 采样单层：主片与 fade 侧按过渡进度交叉淡化；退出中只出 fade 侧。
     *  空层切入的 fadeFromPose（上一帧合成姿态快照）同样作为 fade 侧淡入 */
    private fun sampleLayer(
        layer: LayerState,
        state: AnimationState<*>
    ): Map<String, FloatArray>? {
        val main = layer.clip?.let { samplePose(it, layer.time, layer.loops, state) }
        return when {
            main != null && layer.fadeClip != null && layer.fadeElapsed >= 0f ->
                blendPoses(
                    samplePose(layer.fadeClip!!, layer.fadeTime, layer.fadeLoops, state),
                    main, layer.fadeElapsed / layer.fadeDuration, crossfade = true
                )
            main != null && layer.fadeFromPose != null && layer.fadeElapsed >= 0f ->
                blendPoses(layer.fadeFromPose!!, main, layer.fadeElapsed / layer.fadeDuration, crossfade = true)
            main != null -> main
            else -> layer.fadeClip?.let {
                if (layer.fadeElapsed >= 0f) samplePose(it, layer.fadeTime, layer.fadeLoops, state) else null
            }
        }
    }

    /** 层合成权重：静态权重 × 退出衰减（退出淡化进度内 1 -> 0） */
    private fun layerWeight(layer: LayerState): Float {
        if (!layer.exiting || layer.fadeDuration <= 0f) return layer.weight
        return layer.weight * (1f - layer.fadeElapsed / layer.fadeDuration).coerceIn(0f, 1f)
    }

    /**
     * 构建 GeckoLib MathValue 求值用的轻量 AnimationState：query.* 函数只读
     * queryValues map（缺失返回 0），renderState/manager 不会被触碰。
     * query 值由 Lantern 从实体状态与播放器运动实测换算，未提供的 query 返回 0
     */
    private fun buildEvalState(entity: Entity, living: LivingEntity?): AnimationState<GeoAnimatable> {
        val queries = Reference2DoubleOpenHashMap<Variable>()
        fun q(name: String, value: Double) {
            queries.put(MathParser.getVariableFor(name), value)
        }

        // ANIM_TIME 取最高有内容层的时间轴（表达式关键帧主要在播控/技能动画里 -> ACTION 层优先命中）
        val evalTime = layers.lastOrNull { it.clip != null }?.time ?: 0f
        q(MolangQueries.ANIM_TIME, evalTime.toDouble())
        q(MolangQueries.IS_MOVING, if (movingCached) 1.0 else 0.0)
        q(MolangQueries.GROUND_SPEED, groundSpeed.toDouble())
        q(MolangQueries.VERTICAL_SPEED, verticalSpeed.toDouble())
        q(MolangQueries.IS_ON_GROUND, if (airborne) 0.0 else 1.0)
        q(MolangQueries.LIFE_TIME, entity.tickCount / 20.0)

        val level = entity.level()
        q(MolangQueries.TIME_STAMP, level.gameTime.toDouble())
        q(MolangQueries.TIME_OF_DAY, level.dayTime / 24000.0)
        q(MolangQueries.DAY, level.gameTime / 24000.0)

        if (living != null) {
            q(MolangQueries.IS_ALIVE, if (living.isAlive) 1.0 else 0.0)
            q(MolangQueries.HEALTH, living.health.toDouble())
            q(MolangQueries.MAX_HEALTH, living.maxHealth.toDouble())
            q(MolangQueries.HURT_TIME, living.hurtTime.toDouble())
            q(MolangQueries.IS_IN_WATER, if (living.isInWater) 1.0 else 0.0)
            q(MolangQueries.IS_RIDING, if (living.isPassenger) 1.0 else 0.0)
            q(MolangQueries.IS_SNEAKING, if (living.isShiftKeyDown) 1.0 else 0.0)
            q(MolangQueries.IS_SPRINTING, if (living.isSprinting) 1.0 else 0.0)
            q(MolangQueries.IS_BABY, if (living.isBaby) 1.0 else 0.0)
            q(MolangQueries.SCALE, living.scale.toDouble())
            q(MolangQueries.HEAD_X_ROTATION, living.xRot.toDouble())
            q(MolangQueries.HEAD_Y_ROTATION, living.yRot.toDouble())
            q(MolangQueries.BODY_Y_ROTATION, living.yBodyRot.toDouble())
            q(MolangQueries.YAW_SPEED, (living.yRot - living.yRotO).toDouble())
            q(MolangQueries.DEATH_TICKS, if (dead) death.time * 20.0 else 0.0)
        }
        return AnimationState(null, null, 0f, queries, null)
    }

    /**
     * LOCOMOTION 层全身移动状态机（互斥切换，驻留防抖）：
     * 攀爬 > 游泳 > 飞行 > 骑乘 > 潜行(静止/移动) > 空中 > 疾跑 > walk/idle 兜底。
     * 全身动画必须放本层——若放 POSTURE 会与 LOCOMOTION 的 walk 双层同播，
     * 两套步态逐帧混合=部件分离、生硬卡脚（2026-09-04 真机实证）
     */
    private fun selectLocomotion(
        entity: LivingEntity,
        moving: Boolean,
        states: AnimationStateMapping,
        clips: Map<String, ClipData>
    ): Triple<ClipData, StateConfig, String>? {
        var candidate: Triple<ClipData, StateConfig, String>? = null

        fun pick(vararg keys: String) {
            if (candidate == null) candidate = resolveState(states, clips, *keys)
        }

        if (entity.onClimbable()) pick("climbing")
        if (candidate == null && entity.isInWater) {
            pick(if (moving) "swim_move" else "swim", "swim_walk", "swim_idle")
        }
        if (candidate == null && entity is Player && entity.abilities.flying) {
            pick(if (moving) "fly_move" else "fly_static")
        }
        if (candidate == null && entity.isPassenger) {
            pick(if (moving) "ride_move" else "ride", "riding_walk", "riding_idle")
        }
        if (candidate == null && entity.isShiftKeyDown) {
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
     * POSTURE 层上身姿态链（窄控制面：弓/盾/食/持物，与移动状态并存）：
     * 使用物品(分主副手) > 持物(hold_*)。未命中返回 null 清层
     */
    private fun selectUpperBodyPosture(
        entity: LivingEntity,
        states: AnimationStateMapping,
        clips: Map<String, ClipData>
    ): Triple<ClipData, StateConfig, String>? {
        var candidate: Triple<ClipData, StateConfig, String>? = null

        fun pick(vararg keys: String) {
            if (candidate == null) candidate = resolveState(states, clips, *keys)
        }

        if (entity.isUsingItem) {
            val mainHand = entity.usedItemHand == InteractionHand.MAIN_HAND
            when (entity.useItem.getUseAnimation()) {
                ItemUseAnimation.BOW, ItemUseAnimation.CROSSBOW -> pick(
                    if (mainHand) "use_bow_main" else "use_bow_off", "pull_bow"
                )
                ItemUseAnimation.BLOCK -> pick(
                    if (mainHand) "use_shield_main" else "use_shield_off"
                )
                ItemUseAnimation.EAT, ItemUseAnimation.DRINK -> pick("use_eat", "eat", "drink")
                else -> {}
            }
        } else {
            HoldItems.typeOf(entity.mainHandItem)?.let { type ->
                pick("hold_$type")
            }
        }

        val resolved = candidate ?: return null
        return applyDwell(resolved, states, clips, dwellKey = false)
    }

    /**
     * 驻留防抖（龙核 Trigger "别打断当前" 条件的内核化）：候选状态 ≠ 当前状态且
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

    /** 触发本地一次性动作；剪辑缺失不占槽位，让判定链继续穿透 */
    private fun startLocalAction(config: StateConfig, clips: Map<String, ClipData>) {
        val clip = clips[config.animation] ?: return
        val lengthMs = (clip.length * 1000f).toLong()
        localAction = config
        localActionDeadline = System.currentTimeMillis() +
            (if (lengthMs > 0) lengthMs + 300L else LOCAL_ACTION_FALLBACK_MS)
    }

    /**
     * 位移实测的运动检测（水平移动 + 垂直速度），60ms 采样窗口 + 滞回，
     * 同时维护空中状态与 jump/landing 边沿。返回当前移动判定
     */
    private fun computeMotion(x: Double, y: Double, z: Double): Boolean {
        val now = System.currentTimeMillis()
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

/**
 * 主手物品类型 -> hold_* 状态名（持物待机姿态）。
 * 1.21.10 中剑/镐没有专属 Item 类（工具数据组件化），判定分两层：
 * 先匹配有专属类的类型（专属武器在前），再按数据组件兜底（WEAPON->sword，TOOL->pickaxe）
 */
private object HoldItems {
    private val classes: List<Pair<String, Class<out Item>>> = listOf(
        "mace" to MaceItem::class.java,
        "trident" to TridentItem::class.java,
        "axe" to AxeItem::class.java,
        "shovel" to ShovelItem::class.java,
        "hoe" to HoeItem::class.java,
        "bow" to BowItem::class.java,
        "crossbow" to CrossbowItem::class.java,
        "shield" to ShieldItem::class.java
    )

    fun typeOf(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty) return null
        val item = stack.item
        for ((name, cls) in classes) if (cls.isInstance(item)) return name
        if (stack.has(DataComponents.WEAPON)) return "sword"
        if (stack.has(DataComponents.TOOL)) return "pickaxe"
        return null
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
 *  关键帧值在此按 [state] 求值（数值关键帧是 Constant，求值即字段读） */
private fun samplePose(
    clip: ClipData,
    rawTime: Float,
    loops: Boolean,
    state: AnimationState<*>
): Map<String, FloatArray> {
    val time = when {
        loops && clip.length > 0f -> rawTime % clip.length
        else -> min(max(rawTime, 0f), clip.length)
    }
    val result = HashMap<String, FloatArray>(clip.bones.size)
    for ((boneName, tracks) in clip.bones) {
        val rot = sampleTrack(tracks.rotation, time, state)
        val pos = sampleTrack(tracks.position, time, state)
        val scale = sampleTrack(tracks.scale, time, state)
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
                    b[i].isNaN() -> a[i]
                    a[i].isNaN() -> b[i] * w
                    else -> a[i] + b[i] * w
                }
            }
        } else {
            a
        }
    }
    for ((name, b) in addend) {
        if (name !in out) out[name] = b
    }
    return out
}

private fun sampleTrack(frames: List<Keyframe>, time: Float, state: AnimationState<*>): Vec3? {
    if (frames.isEmpty()) return null
    if (frames.size == 1) return frames[0].value.eval(state)
    if (time <= frames.first().time) return frames.first().value.eval(state)
    if (time >= frames.last().time) return frames.last().value.eval(state)

    var i = 0
    while (i < frames.size - 1 && frames[i + 1].time <= time) i++
    val a = frames[i]
    val b = frames[i + 1]
    val span = b.time - a.time
    if (span <= 0f) return b.value.eval(state)
    val t = (time - a.time) / span

    // 区间端点：prev.post -> next.pre；catmullrom 控制点取 pre 值
    //（ModelEngine 反编译实证：PrePostInterpolator start=prev.post, end=next.pre）
    val start = a.value.eval(state)
    val end = (b.pre ?: b.value).eval(state)

    val catmull = a.lerpMode == "catmullrom" || b.lerpMode == "catmullrom"
    if (!catmull) {
        return Vec3(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        )
    }
    val p0 = frames[max(i - 1, 0)].let { (it.pre ?: it.value).eval(state) }
    val p3 = frames[min(i + 2, frames.size - 1)].let { (it.pre ?: it.value).eval(state) }
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

/**
 * 渲染阶段（submit 内，逐实体串行）调用：将 per-entity 姿势映射写入处理器骨骼。
 * [initial] 为该渲染器克隆骨骼的静态初始姿势（见 GenericGeoModel），剪辑未驱动的
 * 骨骼/轴回落到它——由调用方预计算，避免每帧重新装配
 */
fun applyPoseToBones(
    processor: AnimationProcessor<*>,
    pose: Map<String, FloatArray>?,
    initial: Map<String, FloatArray>
) {
    val bones = processor.registeredBones
    if (bones.isEmpty() || pose == null) return
    for (bone in bones) {
        val init = initial[bone.name] ?: continue
        val v = pose[bone.name]
        bone.updateRotation(
            if (v == null || v[0].isNaN()) init[0] else v[0],
            if (v == null || v[1].isNaN()) init[1] else v[1],
            if (v == null || v[2].isNaN()) init[2] else v[2]
        )
        bone.updatePosition(
            if (v == null || v[3].isNaN()) init[3] else v[3],
            if (v == null || v[4].isNaN()) init[4] else v[4],
            if (v == null || v[5].isNaN()) init[5] else v[5]
        )
        bone.setScaleX(if (v == null || v[6].isNaN()) init[6] else v[6])
        bone.setScaleY(if (v == null || v[7].isNaN()) init[7] else v[7])
        bone.setScaleZ(if (v == null || v[8].isNaN()) init[8] else v[8])
    }
}
