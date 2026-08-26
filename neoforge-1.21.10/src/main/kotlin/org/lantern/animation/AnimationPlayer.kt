package org.lantern.animation

import java.util.UUID
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
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
 * 单一活跃剪辑模型（龙核 model_start/stop 语义，无叠加播放）：
 * 任一时刻只有一个动画驱动全身骨骼，优先级
 * 死亡 > 服务端播控 > 本地一次性动作(jump/landing/spawn) > 姿态链 > 行走(idle/walk)。
 *
 * 状态表驱动（阶段三）：所有状态「配了才生效」，未配置直接穿透到下一优先级，
 * 因此旧 entityModels.yml（只配 idle/walk/...五字段）行为与扩容前完全一致。
 * 姿态链每帧按实体条件直选（ME 状态机语义——条件驱动，条件消失交叉淡化回当前正确状态）。
 *
 * 播放语义：LOOP 回环；ONCE/HOLD 时间轴相同（钳末帧），持有方式不同——
 * ONCE（一次性动作槽）播完回落，HOLD（pull_bow/hold_*）条件消失才回落。
 *
 * 轴变换约定（反编译 GeckoLib 解析器实证）：旋转 (x,y,z) -> (-x,-y,+z) 弧度；
 * 位移、缩放原值。
 */
class AnimationPlayer(private val uuid: UUID) {

    private companion object {
        // 垂直速度窗口阈值（格/秒）：跳跃初速约 8.4，保守边沿 + 滞回区间防抖
        const val RISE_VY = 0.5
        const val FALL_VY = -0.5
        const val GROUNDED_VY = 0.15
        // 窗口位移超过该值视为传送/闪现：重置基准点不产生边沿（防误触发 jump/landing）
        const val TELEPORT_DISTANCE_SQ = 25.0
        // 剪辑长度未知时一次性动作的持有上限（兜底防锁死；landing 语义上限 0.5s 由剪辑长度天然满足）
        const val LOCAL_ACTION_FALLBACK_MS = 500L
    }

    private var activeClip: ClipData? = null
    private var activeTime = 0f
    private var activeSpeed = 1f
    private var activeLoops = true

    // 交叉淡化：旧剪辑继续推进并在过渡时长内淡出
    private var fadeClip: ClipData? = null
    private var fadeTime = 0f
    private var fadeElapsed = -1f
    private var fadeDuration = 0.25f
    private var fadeLoops = true

    private var channelId = -1L
    private var lastFinishReportedId = -1L
    private var dead = false
    private var lastNanos = 0L

    // 本地一次性动作（jump/landing/spawn）：边沿触发，播完回落；被播控覆盖时由截止时间兜底释放
    private var localAction: StateConfig? = null
    private var localActionDeadline = 0L

    var pose: Map<String, FloatArray> = emptyMap()
        private set

    // 位移实测的运动检测：客户端远程实体的 deltaMovement 不同步，用实际位移判定；
    // 结果在采样窗口间保持，双阈值滞回避免边界闪烁。同一窗口顺带产出垂直速度
    // （onGround 也不同步），驱动 jump/landing 边沿与 falling 姿态
    private var lastPos: DoubleArray? = null
    private var lastPosMs = 0L
    private var movingCached = false
    private var airborne = false
    private var wasRising = false
    private var jumpEdge = false
    private var landingEdge = false

    // 最近一次采样窗口的实测速度（molang query: ground_speed / vertical_speed）
    private var groundSpeed = 0f
    private var verticalSpeed = 0f

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

        // --- 死亡状态（进入时锁定，停末帧） ---
        if (living != null && living.isDeadOrDying) {
            dead = true
        } else if (dead) {
            dead = false
        }

        // --- 播控到期（once）：本地时间轴到达剪辑末尾即刻释放；
        //     时间戳兜底仅在剪辑缺失/长度异常时生效。持有末帧等待到期戳会造成
        //     "动画播完仍僵立收招"的空窗（Boss 已在走路而画面停在站姿） ---
        if (forced != null && !forced.loop) {
            val forcedClip = clips[forced.animation]
            val timelineEnded = forcedClip != null && forcedClip.length > 0f &&
                channelId == forced.id && activeTime >= forcedClip.length
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

        // --- 本地一次性动作到期：时间轴播完即释放；被播控覆盖时由截止时间兜底 ---
        localAction?.let { action ->
            val clip = clips[action.animation]
            val timelineEnded = clip != null && clip.length > 0f &&
                activeClip === clip && activeTime >= clip.length
            if (clip == null || timelineEnded || System.currentTimeMillis() >= localActionDeadline) {
                localAction = null
            }
        }

        // --- 边沿触发：spawn（实体生命周期内首次渲染）/ jump（起跳）/ landing（落地）。
        //     jump/landing 排除水中与骑乘：游泳上浮、载具颠簸同样产生垂直速度边沿，
        //     且一次性动作槽优先于姿态链，swim/riding 姿态拦不住误触发 ---
        if (firstRender) {
            states.config("spawn")?.let { startLocalAction(it, clips) }
        }
        val edgeAllowed = living != null && !living.isInWater && !living.isPassenger
        if (jumpEdge) {
            jumpEdge = false
            if (edgeAllowed) states.config("jump")?.let { startLocalAction(it, clips) }
        }
        if (landingEdge) {
            landingEdge = false
            if (edgeAllowed) states.config("landing")?.let { startLocalAction(it, clips) }
        }

        // --- 每帧直选目标剪辑 ---
        val target: ClipData?
        val targetConfig: StateConfig?
        val targetSpeed: Float
        val blendSeconds: Float
        val loops: Boolean
        when {
            dead -> {
                targetConfig = states.config("death")
                target = states.death?.let { clips[it] }
                targetSpeed = 1f
                blendSeconds = (targetConfig?.transition ?: 5) * 0.05f
                loops = false
            }
            activeForced != null -> {
                if (activeForced.id != channelId) channelId = activeForced.id
                targetConfig = null
                target = clips[activeForced.animation]
                targetSpeed = activeForced.speed
                // 播控切入的过渡时长由服务端指令指定（tick × 50ms）；0 = 立即切换（如死亡）。
                // 协议默认 5 tick = 0.25s，与行走层淡化时长一致，技能动画观感不变
                blendSeconds = activeForced.transition * 0.05f
                loops = activeForced.loop
            }
            localAction != null -> {
                val action = localAction!!
                targetConfig = action
                target = clips[action.animation]
                targetSpeed = 1f
                blendSeconds = action.transition * 0.05f
                loops = action.playMode == PlayMode.LOOP
            }
            else -> {
                channelId = -1L
                val posture = living?.let { selectPosture(it, isMoving, states, clips) }
                if (posture != null) {
                    target = posture.first
                    targetConfig = posture.second
                    blendSeconds = posture.second.transition * 0.05f
                    loops = posture.second.playMode == PlayMode.LOOP
                } else {
                    val config = states.config(if (isMoving) "walk" else "idle")
                        ?: StateConfig(if (isMoving) states.walk else states.idle)
                    target = clips[config.animation]
                    targetConfig = config
                    blendSeconds = config.transition * 0.05f
                    loops = config.playMode == PlayMode.LOOP
                }
                targetSpeed = 1f
            }
        }

        // --- 切换：旧剪辑进入淡出（过渡为 0 时立即切换，无淡化） ---
        if (target !== activeClip) {
            if (activeClip != null && target != null && blendSeconds > 0f) {
                fadeClip = activeClip
                fadeTime = activeTime
                fadeElapsed = 0f
                fadeDuration = blendSeconds
                fadeLoops = activeLoops
            } else {
                fadeClip = null
                fadeElapsed = -1f
            }
            activeClip = target
            // 真死亡后的任何切换钉死末帧：无论从哪条路径切入 death 剪辑，
            // 都不从第 0 帧重播（消除"站起来一下再死"）
            activeTime = if (dead && target != null) target.length else 0f
            activeLoops = loops
        }
        activeSpeed = targetSpeed
        activeClip?.let { activeTime = advance(it, activeTime, dt * activeSpeed, activeLoops) }
        fadeClip?.let { fadeTime = advance(it, fadeTime, dt, fadeLoops) }

        // 死亡状态无条件钉死：清除一切交叉淡化、时间钳制到剪辑末帧。
        // 任何残留的 fadeClip 都会把"躺倒姿势"拉向"站立姿势"——视觉即"站起来"
        if (dead && activeClip != null) {
            activeTime = activeClip?.length ?: 0f
            fadeClip = null
            fadeElapsed = -1f
        }

        // --- 采样 + 交叉淡化 ---
        // 表达式关键帧在此求值：query.* 来自 Lantern 构建的 per-entity queryValues，
        // variable.* 来自 packet 17 注入的 per-entity 变量（ThreadLocal 上下文）
        val evalState = buildEvalState(entity, living)
        val vars = MolangVariableStore.resolve(uuid, evalState)
        pose = MolangContext.evaluate(vars) {
            val targetPose = activeClip?.let { samplePose(it, activeTime, activeLoops, evalState) } ?: emptyMap()
            if (fadeClip != null && fadeElapsed >= 0f) {
                fadeElapsed += dt
                if (fadeElapsed >= fadeDuration) {
                    fadeClip = null
                    fadeElapsed = -1f
                    targetPose
                } else {
                    blendPoses(
                        samplePose(fadeClip!!, fadeTime, fadeLoops, evalState),
                        targetPose,
                        fadeElapsed / fadeDuration
                    )
                }
            } else {
                targetPose
            }
        }
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

        q(MolangQueries.ANIM_TIME, activeTime.toDouble())
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
            q(MolangQueries.DEATH_TICKS, if (dead) activeTime * 20.0 else 0.0)
        }
        return AnimationState(null, null, 0f, queries, null)
    }

    /**
     * 姿态链直选（优先级从高到低，全部「配了且剪辑存在」才命中）：
     * 使用物品(弓弩蓄力/进食/饮水) > 持物(hold_*) > 攀爬 > 游泳 > 骑乘 > 潜行 > 空中 > 冲刺。
     * 都未命中返回 null，回落行走层
     */
    private fun selectPosture(
        entity: LivingEntity,
        moving: Boolean,
        states: AnimationStateMapping,
        clips: Map<String, ClipData>
    ): Pair<ClipData, StateConfig>? {
        var candidate: StateConfig? = null

        if (entity.isUsingItem) {
            when (entity.useItem.getUseAnimation()) {
                ItemUseAnimation.BOW, ItemUseAnimation.CROSSBOW -> candidate = states.config("pull_bow")
                ItemUseAnimation.EAT -> candidate = states.config("eat")
                ItemUseAnimation.DRINK -> candidate = states.config("drink")
                else -> {}
            }
        } else {
            HoldItems.typeOf(entity.mainHandItem)?.let { type ->
                candidate = states.config("hold_$type")
            }
        }
        if (candidate == null && entity.onClimbable()) candidate = states.config("climbing")
        if (candidate == null && entity.isInWater) {
            candidate = states.config(if (moving) "swim_walk" else "swim_idle")
        }
        if (candidate == null && entity.isPassenger) {
            candidate = states.config(if (moving) "riding_walk" else "riding_idle")
        }
        if (candidate == null && entity.isShiftKeyDown) {
            candidate = states.config(if (moving) "sneak_walk" else "sneak_idle")
        }
        if (candidate == null && airborne) candidate = states.config("falling")
        if (candidate == null && entity.isSprinting && moving) candidate = states.config("sprint")

        // 配置了但动画文件里没有该动画：穿透回行走，而不是冻在原地
        candidate?.let { config -> clips[config.animation]?.let { return it to config } }
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
        if (elapsed < 60) return movingCached

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
        if (airborne && grounded && !rising && !fallingNow) landingEdge = true
        if (rising && !wasRising) jumpEdge = true
        // 空中滞回：起跳/下落进入，速度回静（落地）退出；中间速度段保持原状态
        airborne = rising || fallingNow || (airborne && !grounded)
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

/** 按剪辑播放语义推进时间（回环/钳末帧由播放器侧配置决定，而非剪辑 json 的 loop 字段） */
private fun advance(clip: ClipData, time: Float, dt: Float, loops: Boolean): Float {
    val next = time + dt
    return if (loops && clip.length > 0f) next % clip.length else min(next, clip.length)
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

/** 两姿势按权重混合；只在单侧出现的骨骼/轴取另一侧值，随权重渐现/渐隐 */
private fun blendPoses(from: Map<String, FloatArray>, to: Map<String, FloatArray>, w: Float): Map<String, FloatArray> {
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
        } else {
            a
        }
    }
    // 只在 to 独有的骨骼上补齐，避免合并键集的中间 Set 分配
    for ((name, b) in to) {
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
