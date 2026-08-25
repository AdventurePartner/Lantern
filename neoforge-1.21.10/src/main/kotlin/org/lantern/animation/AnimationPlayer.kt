package org.lantern.animation

import java.util.UUID
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.renderstate.AnimationControlStore
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.animatable.processing.AnimationProcessor
import software.bernie.geckolib.cache.`object`.GeoBone
import kotlin.math.max
import kotlin.math.min

/**
 * Lantern 自托管动画播放器（每实体一个）。
 *
 * 单一活跃剪辑模型（龙核 model_start/stop 语义，无叠加播放）：
 * 任一时刻只有一个动画驱动全身骨骼，优先级 死亡 > 服务端播控 > 行走。
 * 行走层每帧按移动状态直选 idle/walk（ME 状态机语义——条件驱动，技能结束
 * 直接交叉淡化到当前正确状态，行走动画中段无缝接续）。
 *
 * 轴变换约定（反编译 GeckoLib 解析器实证）：旋转 (x,y,z) -> (-x,-y,+z) 弧度；
 * 位移、缩放原值。
 */
class AnimationPlayer(private val uuid: UUID) {

    private companion object {
        const val BLEND_SECONDS = 0.25f
    }

    private var activeClip: ClipData? = null
    private var activeTime = 0f
    private var activeSpeed = 1f

    // 交叉淡化：旧剪辑继续推进并在过渡时长内淡出
    private var fadeClip: ClipData? = null
    private var fadeTime = 0f
    private var fadeElapsed = -1f
    private var fadeDuration = BLEND_SECONDS

    private var channelId = -1L
    private var lastFinishReportedId = -1L
    private var dead = false
    private var lastNanos = 0L

    var pose: Map<String, FloatArray> = emptyMap()
        private set

    // 位移实测的移动检测：客户端远程实体的 deltaMovement 不同步，
    // 用实际水平速度判定；结果在采样窗口间保持，双阈值滞回避免边界闪烁
    private var lastPos: DoubleArray? = null
    private var lastPosMs = 0L
    private var movingCached = false

    fun drive(
        clips: Map<String, ClipData>,
        forced: AnimationControlStore.ForcedAnimation?,
        actionState: EntityAnimationState?,
        posX: Double,
        posY: Double,
        posZ: Double,
        states: AnimationStateMapping
    ) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1_000_000_000f, 0.25f)
        lastNanos = now
        val isMoving = computeMoving(posX, posY, posZ)

        // --- 死亡状态（进入时锁定，停末帧） ---
        if (actionState == EntityAnimationState.DEATH) {
            dead = true
        } else if (dead && actionState == null) {
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

        // --- 每帧直选目标剪辑：死亡 > 播控 > 行走 ---
        val target: ClipData?
        val targetSpeed: Float
        val blendSeconds: Float
        when {
            dead -> {
                target = states.death?.let { clips[it] }
                targetSpeed = 1f
                blendSeconds = BLEND_SECONDS
            }
            activeForced != null -> {
                if (activeForced.id != channelId) channelId = activeForced.id
                target = clips[activeForced.animation]
                targetSpeed = activeForced.speed
                // 播控切入的过渡时长由服务端指令指定（tick × 50ms）；0 = 立即切换（如死亡）。
                // 协议默认 5 tick = 0.25s，与行走层淡化时长一致，技能动画观感不变
                blendSeconds = activeForced.transition * 0.05f
            }
            else -> {
                channelId = -1L
                target = if (isMoving) clips[states.walk] else clips[states.idle]
                targetSpeed = 1f
                blendSeconds = BLEND_SECONDS
            }
        }

        // --- 切换：旧剪辑进入淡出（过渡为 0 时立即切换，无淡化） ---
        if (target !== activeClip) {
            if (activeClip != null && target != null && blendSeconds > 0f) {
                fadeClip = activeClip
                fadeTime = activeTime
                fadeElapsed = 0f
                fadeDuration = blendSeconds
            } else {
                fadeClip = null
                fadeElapsed = -1f
            }
            activeClip = target
            // 真死亡后的任何切换钉死末帧：无论从哪条路径切入 death 剪辑，
            // 都不从第 0 帧重播（消除"站起来一下再死"）
            activeTime = if (dead && target != null) target.length else 0f
        }
        activeSpeed = targetSpeed
        activeClip?.let { activeTime = advance(it, activeTime, dt * activeSpeed) }
        fadeClip?.let { fadeTime = advance(it, fadeTime, dt) }

        // 死亡状态无条件钉死：清除一切交叉淡化、时间钳制到剪辑末帧。
        // 任何残留的 fadeClip 都会把"躺倒姿势"拉向"站立姿势"——视觉即"站起来"
        if (dead && activeClip != null) {
            activeTime = activeClip?.length ?: 0f
            fadeClip = null
            fadeElapsed = -1f
        }

        // --- 采样 + 交叉淡化 ---
        val targetPose = activeClip?.let { samplePose(it, activeTime) } ?: emptyMap()
        pose = if (fadeClip != null && fadeElapsed >= 0f) {
            fadeElapsed += dt
            if (fadeElapsed >= fadeDuration) {
                fadeClip = null
                fadeElapsed = -1f
                targetPose
            } else {
                blendPoses(samplePose(fadeClip!!, fadeTime), targetPose, fadeElapsed / fadeDuration)
            }
        } else {
            targetPose
        }
    }

    private fun computeMoving(x: Double, y: Double, z: Double): Boolean {
        val now = System.currentTimeMillis()
        val last = lastPos
        if (last == null) {
            lastPos = doubleArrayOf(x, y, z)
            lastPosMs = now
            return movingCached
        }
        val elapsed = now - lastPosMs
        if (elapsed >= 60) {
            val dx = x - last[0]
            val dz = z - last[2]
            val secs = elapsed / 1000.0
            val speedSq = (dx * dx + dz * dz) / (secs * secs)
            movingCached = if (movingCached) speedSq > 0.04 else speedSq > 0.09
            lastPos = doubleArrayOf(x, y, z)
            lastPosMs = now
        }
        return movingCached
    }
}

/** 按剪辑循环语义推进时间 */
private fun advance(clip: ClipData, time: Float, dt: Float): Float {
    val next = time + dt
    return if (clip.loops && clip.length > 0f) next % clip.length else min(next, clip.length)
}

/** 采样剪辑 -> 骨骼写入空间姿势（轴变换在此完成；缺失轨道记 NaN = 该轴回落静态值） */
private fun samplePose(clip: ClipData, rawTime: Float): Map<String, FloatArray> {
    val time = when {
        clip.loops && clip.length > 0f -> rawTime % clip.length
        else -> min(max(rawTime, 0f), clip.length)
    }
    val result = HashMap<String, FloatArray>(clip.bones.size)
    for ((boneName, tracks) in clip.bones) {
        val rot = sampleTrack(tracks.rotation, time)
        val pos = sampleTrack(tracks.position, time)
        val scale = sampleTrack(tracks.scale, time)
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

private fun sampleTrack(frames: List<Keyframe>, time: Float): Vec3? {
    if (frames.isEmpty()) return null
    if (frames.size == 1) return frames[0].value
    if (time <= frames.first().time) return frames.first().value
    if (time >= frames.last().time) return frames.last().value

    var i = 0
    while (i < frames.size - 1 && frames[i + 1].time <= time) i++
    val a = frames[i]
    val b = frames[i + 1]
    val span = b.time - a.time
    if (span <= 0f) return b.value
    val t = (time - a.time) / span

    // 区间端点：prev.post -> next.pre；catmullrom 控制点取 pre 值
    //（ModelEngine 反编译实证：PrePostInterpolator start=prev.post, end=next.pre）
    val start = a.value
    val end = b.pre ?: b.value

    val catmull = a.lerpMode == "catmullrom" || b.lerpMode == "catmullrom"
    if (!catmull) {
        return Vec3(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        )
    }
    val p0 = frames[max(i - 1, 0)].let { it.pre ?: it.value }
    val p3 = frames[min(i + 2, frames.size - 1)].let { it.pre ?: it.value }
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
