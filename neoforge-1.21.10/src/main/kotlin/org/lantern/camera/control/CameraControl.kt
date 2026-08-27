package org.lantern.camera.control

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 相机演出状态机（packet 18：lock/unlock/shake/fov/offset/clear/path/watch）。
 *
 * 全部状态只在渲染线程触碰（NetworkParser 经 Minecraft.execute 派发，与 Camera.setup /
 * GameRenderer.getFov 同线程）。叠加顺序（策划 §6.4）：
 * 越肩 → offset → lock → **path/watch 位姿覆写** → shake（位置扰动）。
 * path/watch 结束或被 clear 打断时经 250ms 回程过渡滑回玩家位姿（不瞬跳）；
 * path 入口有 5 tick 淡入（从当前位姿滑进首帧）。
 */
object CameraControl {

    private fun now(): Long = Util.getMillis()

    // ---- offset：pitch/yaw/roll 叠加，带过渡 ----
    private var offsetPitch = 0f
    private var offsetYaw = 0f
    private var offsetRoll = 0f
    private var offsetStartPitch = 0f
    private var offsetStartYaw = 0f
    private var offsetStartRoll = 0f
    private var offsetTargetPitch = 0f
    private var offsetTargetYaw = 0f
    private var offsetTargetRoll = 0f
    private var offsetStartMs = 0L
    private var offsetTransitionMs = 0L

    // ---- lock：看向坐标或实体 ----
    private var lockEntity: UUID? = null
    private var lockX = 0.0
    private var lockY = 0.0
    private var lockZ = 0.0
    private var lockSmoothMs = 0L
    private var lockStartMs = 0L
    private var lockEndMs = 0L
    private var lockSync = false

    // ---- shake：位置扰动 ----
    private var shakeAmplitude = 0f
    private var shakeFrequency = 8f
    private var shakeDurationMs = 0L
    private var shakeDecay = true
    private var shakeStartMs = 0L
    private var shakePhaseX = 0f
    private var shakePhaseY = 0f

    // ---- fov（手动指令轨）----
    private var fovTarget: Float? = null
    private var fovStart: Float? = null
    private var fovLast = 0f
    private var fovStartMs = 0L
    private var fovTransitionMs = 0L
    private var fovReleasing = false

    // ---- path：关键帧位姿覆写 ----
    /** yaw/pitch 已按相邻帧解卷（catmullrom 连续、linear 等价最短路径） */
    private class PathFrame(
        val t: Double,
        val x: Double, val y: Double, val z: Double,
        val yaw: Double, val pitch: Double,
        val fov: Double?,
        val interp: String
    )

    private var pathFrames: List<PathFrame>? = null
    private var pathFovFrames: List<PathFrame>? = null
    private var pathTotal = 0.0
    private var pathLoop = false
    private var pathSpeed = 1.0
    private var pathStartMs = 0L
    private var pathEntry: CapturedPose? = null

    // ---- watch：固定点观察（看向坐标或实体，动态角度）----
    private var watchX = 0.0
    private var watchY = 0.0
    private var watchZ = 0.0
    private var watchLook: Vec3? = null
    private var watchLookEntity: UUID? = null
    private var watchStartMs = 0L
    private var watchSmoothMs = 0L
    private var watchEndMs = 0L
    private var watchFrom: CapturedPose? = null

    // ---- 回程过渡与覆写缓存 ----
    private class CapturedPose(
        val yaw: Float, val pitch: Float,
        val x: Double, val y: Double, val z: Double
    )

    private val FINISH_MS = 250L
    private var finishPose: CapturedPose? = null
    private var finishFov: Float? = null
    private var finishStartMs = 0L
    private var overrideCache: CapturedPose? = null
    private var overrideFovCache: Float? = null

    data class Pose(
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
        /** 非空 = 位置直接覆写（path/watch/回程），否则只做相对扰动 */
        val absolutePos: Vec3?,
        val dx: Double,
        val dy: Double,
        val dz: Double
    )

    fun handle(action: String, obj: JsonObject) {
        when (action) {
            "lock" -> {
                val entityUuid = obj.get("entity")?.asString
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val target = obj.getAsJsonObject("target")
                when {
                    entityUuid != null -> lockEntity = entityUuid
                    target != null -> {
                        lockEntity = null
                        lockX = target.get("x")?.asDouble ?: 0.0
                        lockY = target.get("y")?.asDouble ?: 0.0
                        lockZ = target.get("z")?.asDouble ?: 0.0
                    }
                    else -> return
                }
                lockSmoothMs = ((obj.get("smooth")?.asDouble ?: 0.3).coerceAtLeast(0.0) * 1000).toLong()
                lockStartMs = now()
                val duration = obj.get("duration")?.asDouble ?: 0.0
                lockEndMs = if (duration <= 0.0) Long.MAX_VALUE else now() + (duration * 1000).toLong()
                lockSync = obj.get("sync")?.asBoolean ?: false
            }
            "unlock" -> {
                lockEntity = null
                lockEndMs = 0L
            }
            "shake" -> {
                shakeAmplitude = (obj.get("amplitude")?.asDouble ?: 0.3).toFloat().coerceAtLeast(0f)
                shakeFrequency = (obj.get("frequency")?.asDouble ?: 8.0).toFloat().coerceAtLeast(0.1f)
                shakeDurationMs = ((obj.get("duration")?.asDouble ?: 0.5).coerceAtLeast(0.05) * 1000).toLong()
                shakeDecay = obj.get("decay")?.asBoolean ?: true
                shakeStartMs = now()
                shakePhaseX = (Math.random() * Math.PI * 2).toFloat()
                shakePhaseY = (Math.random() * Math.PI * 2).toFloat()
            }
            "fov" -> {
                val value = if (obj.has("value") && !obj.get("value").isJsonNull) obj.get("value").asDouble else null
                fovStartMs = now()
                fovTransitionMs = ((obj.get("transition")?.asDouble ?: 1.0).coerceAtLeast(0.0) * 1000).toLong()
                // 起始值取当前生效值（含恢复中途打断），闲置时惰性捕获 vanilla——避免瞬跳
                fovStart = if (fovTarget != null || fovReleasing) fovLast else null
                if (value != null) {
                    fovTarget = value.toFloat().coerceIn(1f, 170f)
                    fovReleasing = false
                } else {
                    fovReleasing = true
                }
            }
            "offset" -> {
                offsetStartPitch = offsetPitch
                offsetStartYaw = offsetYaw
                offsetStartRoll = offsetRoll
                offsetTargetPitch = (obj.get("pitch")?.asDouble ?: 0.0).toFloat()
                offsetTargetYaw = (obj.get("yaw")?.asDouble ?: 0.0).toFloat()
                offsetTargetRoll = (obj.get("roll")?.asDouble ?: 0.0).toFloat()
                offsetStartMs = now()
                offsetTransitionMs = ((obj.get("transition")?.asDouble ?: 2.0).coerceAtLeast(0.0) * 1000).toLong()
            }
            "path" -> startPath(obj)
            "watch" -> startWatch(obj)
            "clear" -> clear()
        }
    }

    // ============ path 播放 ============

    private fun startPath(obj: JsonObject) {
        val array: JsonArray = obj.getAsJsonArray("keyframes") ?: return
        if (array.size() == 0) return
        val frames = ArrayList<PathFrame>(array.size())
        var lastYaw = 0.0
        var lastPitch = 0.0
        for (element in array) {
            if (!element.isJsonObject) continue
            val frame = element.asJsonObject
            val interp = frame.get("interp")?.asString?.takeIf { it == "linear" || it == "smooth" || it == "hold" }
                ?: "smooth"
            val rawYaw = frame.get("yaw")?.asDouble ?: 0.0
            val rawPitch = frame.get("pitch")?.asDouble ?: 0.0
            // 相邻帧解卷：catmullrom 需要连续序列，解卷后 linear 恰好等价最短路径
            val yaw = if (frames.isEmpty()) rawYaw
            else lastYaw + Mth.wrapDegrees((rawYaw - lastYaw).toFloat()).toDouble()
            val pitch = if (frames.isEmpty()) rawPitch
            else lastPitch + Mth.wrapDegrees((rawPitch - lastPitch).toFloat()).toDouble()
            frames.add(
                PathFrame(
                    (frame.get("t")?.asDouble ?: 0.0).coerceAtLeast(0.0),
                    frame.get("x")?.asDouble ?: 0.0,
                    frame.get("y")?.asDouble ?: 0.0,
                    frame.get("z")?.asDouble ?: 0.0,
                    yaw, pitch,
                    frame.get("fov")?.takeIf { it.isJsonPrimitive }?.asDouble,
                    interp
                )
            )
            lastYaw = yaw
            lastPitch = pitch
        }
        if (frames.isEmpty()) return
        frames.sortBy { it.t }
        pathFrames = frames
        pathFovFrames = frames.filter { it.fov != null }.ifEmpty { null }
        pathTotal = frames.last().t.coerceAtLeast(1.0)
        pathLoop = obj.get("loop")?.asBoolean ?: false
        pathSpeed = (obj.get("speed")?.asDouble ?: 1.0).coerceIn(0.05, 10.0)
        pathStartMs = now()
        pathEntry = null
    }

    /** 定位 t 所在段：返回 (起始帧下标, 段内进度 u)；i=-1 表示首帧之前，i=last 表示末帧。 */
    private fun segmentAt(frames: List<PathFrame>, t: Double): Pair<Int, Double> {
        if (t <= frames.first().t) return -1 to 0.0
        val last = frames.size - 1
        if (t >= frames[last].t) return last to 1.0
        for (i in 0 until last) {
            if (t < frames[i + 1].t) {
                val span = frames[i + 1].t - frames[i].t
                val u = if (span <= 0.0) 1.0 else (t - frames[i].t) / span
                return i to u
            }
        }
        return last to 1.0
    }

    /** 按目标帧 interp 求分量值：hold=保持起始帧，linear=线性，smooth=catmullrom（邻帧钳制）。 */
    private fun evalComponent(frames: List<PathFrame>, i: Int, u: Double, pick: (PathFrame) -> Double): Double {
        val last = frames.size - 1
        return when {
            i < 0 -> pick(frames[0])
            i >= last -> pick(frames[last])
            else -> when (frames[i + 1].interp) {
                "hold" -> pick(frames[i])
                "linear" -> pick(frames[i]) + (pick(frames[i + 1]) - pick(frames[i])) * u
                else -> {
                    val p0 = frames[(i - 1).coerceIn(0, last)]
                    val p1 = frames[i]
                    val p2 = frames[i + 1]
                    val p3 = frames[(i + 2).coerceIn(0, last)]
                    val t2 = u * u
                    val t3 = t2 * u
                    0.5 * ((2.0 * pick(p1)) +
                        (-pick(p0) + pick(p2)) * u +
                        (2.0 * pick(p0) - 5.0 * pick(p1) + 4.0 * pick(p2) - pick(p3)) * t2 +
                        (-pick(p0) + 3.0 * pick(p1) - 3.0 * pick(p2) + pick(p3)) * t3)
                }
            }
        }
    }

    private fun pathTime(time: Long): Double? {
        val frames = pathFrames ?: return null
        val elapsedTicks = (time - pathStartMs) * pathSpeed / 50.0
        if (!pathLoop && elapsedTicks >= pathTotal) {
            // 自然结束：末帧姿态进入回程过渡
            val last = frames.last()
            val (i, u) = segmentAt(frames, pathTotal)
            beginFinish(
                CapturedPose(
                    evalComponent(frames, i, u) { it.yaw }.toFloat(),
                    evalComponent(frames, i, u) { it.pitch }.toFloat(),
                    evalComponent(frames, i, u) { it.x },
                    evalComponent(frames, i, u) { it.y },
                    evalComponent(frames, i, u) { it.z }
                ),
                lastFovValue()
            )
            pathFrames = null
            pathFovFrames = null
            pathEntry = null
            return null
        }
        return if (pathLoop) elapsedTicks % pathTotal else elapsedTicks.coerceAtMost(pathTotal)
    }

    private fun lastFovValue(): Float? {
        val fovFrames = pathFovFrames ?: return null
        val (i, u) = segmentAt(fovFrames, pathTotal)
        return evalComponent(fovFrames, i, u) { it.fov!! }.toFloat()
    }

    // ============ watch ============

    private fun startWatch(obj: JsonObject) {
        val pos = obj.getAsJsonObject("pos") ?: return
        watchX = pos.get("x")?.asDouble ?: 0.0
        watchY = pos.get("y")?.asDouble ?: 0.0
        watchZ = pos.get("z")?.asDouble ?: 0.0
        val look = obj.getAsJsonObject("look")
        watchLook = look?.let {
            Vec3(it.get("x")?.asDouble ?: 0.0, it.get("y")?.asDouble ?: 0.0, it.get("z")?.asDouble ?: 0.0)
        }
        watchLookEntity = obj.get("entity")?.asString
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (watchLook == null && watchLookEntity == null) return
        watchStartMs = now()
        watchSmoothMs = ((obj.get("smooth")?.asDouble ?: 1.0).coerceAtLeast(0.0) * 1000).toLong()
        val duration = obj.get("duration")?.asDouble ?: 0.0
        watchEndMs = if (duration <= 0.0) Long.MAX_VALUE else now() + (duration * 1000).toLong()
        watchFrom = null
    }

    // ============ 通用 ============

    private fun beginFinish(pose: CapturedPose, fov: Float?) {
        finishPose = pose
        finishFov = fov
        finishStartMs = now()
    }

    /** 演出状态清空：path/watch 经回程过渡，offset/fov 快速归零，lock/shake 立即停（不影响越肩）。 */
    fun clear() {
        val captured = overrideCache
        if (captured != null) {
            beginFinish(captured, overrideFovCache)
        } else if (overrideFovCache != null) {
            finishFov = overrideFovCache
            finishStartMs = now()
        }
        pathFrames = null
        pathFovFrames = null
        pathEntry = null
        watchEndMs = 0L
        watchFrom = null
        overrideCache = null
        overrideFovCache = null
        offsetStartPitch = offsetPitch
        offsetStartYaw = offsetYaw
        offsetStartRoll = offsetRoll
        offsetTargetPitch = 0f
        offsetTargetYaw = 0f
        offsetTargetRoll = 0f
        offsetStartMs = now()
        offsetTransitionMs = 200L
        lockEntity = null
        lockEndMs = 0L
        shakeAmplitude = 0f
        fovStart = if (fovTarget != null || fovReleasing) fovLast else null
        fovStartMs = now()
        fovTransitionMs = 300L
        fovReleasing = true
    }

    /** 断线清理：全部立即归零（无过渡）。 */
    fun hardReset() {
        offsetPitch = 0f
        offsetYaw = 0f
        offsetRoll = 0f
        offsetTargetPitch = 0f
        offsetTargetYaw = 0f
        offsetTargetRoll = 0f
        lockEntity = null
        lockEndMs = 0L
        shakeAmplitude = 0f
        fovTarget = null
        fovStart = null
        fovReleasing = false
        pathFrames = null
        pathFovFrames = null
        pathEntry = null
        watchEndMs = 0L
        watchFrom = null
        finishPose = null
        finishFov = null
        overrideCache = null
        overrideFovCache = null
    }

    /**
     * 是否有影响朝向的演出状态激活（offset 过渡中/非零、lock、path/watch 或回程中）。
     * 供拾取修正门控：视图被旋转/接管时，射线也要跟准星。
     */
    @JvmStatic
    fun orientationActive(): Boolean {
        val time = now()
        val offsetEngaged = time < offsetStartMs + offsetTransitionMs ||
            offsetTargetPitch != 0f || offsetTargetYaw != 0f || offsetTargetRoll != 0f ||
            offsetPitch != 0f || offsetYaw != 0f || offsetRoll != 0f
        return offsetEngaged || lockEndMs > time ||
            pathFrames != null || watchEndMs > 0L || finishPose != null
    }

    /**
     * 每帧在 Camera.setup 尾部求值：输入 vanilla 产出的基础朝向与相机位置，
     * 返回叠加演出效果后的完整位姿；无任何演出激活时返回 null（零开销）。
     */
    @JvmStatic
    fun apply(baseYaw: Float, basePitch: Float, partialTick: Float,
              camX: Double, camY: Double, camZ: Double): Pose? {
        val time = now()
        var yaw = baseYaw
        var pitch = basePitch
        var roll = 0f
        var absolute: Vec3? = null
        var engaged = false

        val finish = finishPose
        if (finish != null) {
            // 回程过渡：覆写姿态 → 玩家跟随姿态
            val w = if (FINISH_MS <= 0L) 1f
            else ((time - finishStartMs).toFloat() / FINISH_MS).coerceIn(0f, 1f)
            val e = smooth(w)
            yaw = Mth.rotLerp(e, finish.yaw, baseYaw)
            pitch = lerp(e, finish.pitch, basePitch)
            absolute = Vec3(
                finish.x + (camX - finish.x) * e,
                finish.y + (camY - finish.y) * e,
                finish.z + (camZ - finish.z) * e)
            if (w >= 1f) {
                finishPose = null
                finishFov = null
            }
            engaged = true
        } else {
            // path / watch 位姿覆写（优先于 offset/lock——整段接管朝向与位置）
            val override = evaluateOverride(time, baseYaw, basePitch, partialTick, camX, camY, camZ)
            if (override != null) {
                yaw = override.yaw
                pitch = override.pitch
                absolute = override.pos
                overrideCache = CapturedPose(yaw, pitch, absolute.x, absolute.y, absolute.z)
                engaged = true
            } else {
                // offset：朝向叠加（角度直接相加，pitch 钳制）
                val settled = time >= offsetStartMs + offsetTransitionMs
                val targetNonZero = offsetTargetPitch != 0f || offsetTargetYaw != 0f || offsetTargetRoll != 0f
                val currentNonZero = offsetPitch != 0f || offsetYaw != 0f || offsetRoll != 0f
                if (!settled || targetNonZero || currentNonZero) {
                    engaged = true
                    if (settled || offsetTransitionMs <= 0L) {
                        offsetPitch = offsetTargetPitch
                        offsetYaw = offsetTargetYaw
                        offsetRoll = offsetTargetRoll
                    } else {
                        val w = smooth(((time - offsetStartMs).toFloat() / offsetTransitionMs).coerceIn(0f, 1f))
                        offsetPitch = lerp(w, offsetStartPitch, offsetTargetPitch)
                        offsetYaw = lerp(w, offsetStartYaw, offsetTargetYaw)
                        offsetRoll = lerp(w, offsetStartRoll, offsetTargetRoll)
                    }
                    yaw = baseYaw + offsetYaw
                    pitch = (basePitch + offsetPitch).coerceIn(-89.9f, 89.9f)
                    roll = offsetRoll
                }

                // lock：朝向覆写（平滑渐入，目标每帧重取——可跟随移动实体）
                if (lockEndMs > time) {
                    val targetPos = lockTarget(partialTick)
                    if (targetPos != null) {
                        engaged = true
                        val dx = targetPos.x - camX
                        val dy = targetPos.y - camY
                        val dz = targetPos.z - camZ
                        if (dx * dx + dy * dy + dz * dz > 1e-6) {
                            val desiredYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
                            val desiredPitch = -Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
                            var w = 1f
                            if (lockSmoothMs > 0L) {
                                w = ((time - lockStartMs).toFloat() / lockSmoothMs).coerceIn(0f, 1f)
                            }
                            w = smooth(w)
                            yaw = Mth.rotLerp(w, yaw, desiredYaw)
                            pitch = lerp(w, pitch, desiredPitch)
                            if (lockSync) {
                                Minecraft.getInstance().player?.let {
                                    it.setYRot(yaw)
                                    it.setXRot(pitch)
                                }
                            }
                        }
                    }
                }
            }
        }

        // shake：位置扰动（世界 Y + 视线水平右向量），默认线性衰减——叠加在覆写/常规位姿之后
        var sx = 0f
        var sy = 0f
        if (shakeAmplitude > 0f) {
            val elapsedMs = time - shakeStartMs
            if (elapsedMs >= shakeDurationMs) {
                shakeAmplitude = 0f
            } else {
                engaged = true
                val elapsed = elapsedMs / 1000.0
                val factor = if (shakeDecay) {
                    (1.0 - elapsed / (shakeDurationMs / 1000.0)).coerceAtLeast(0.0)
                } else 1.0
                val omega = 2.0 * Math.PI * shakeFrequency
                sx = (shakeAmplitude * 0.6 * factor * sin(omega * elapsed + shakePhaseX)).toFloat()
                sy = (shakeAmplitude * factor * sin(omega * 1.27 * elapsed + shakePhaseY)).toFloat()
            }
        }

        if (!engaged) return null
        val yawRad = Math.toRadians(yaw.toDouble())
        val rightX = -Math.cos(yawRad)
        val rightZ = -Math.sin(yawRad)
        return Pose(yaw, pitch, roll, absolute, rightX * sx, sy.toDouble(), rightZ * sx)
    }

    private class OverridePose(val yaw: Float, val pitch: Float, val pos: Vec3)

    private fun evaluateOverride(
        time: Long, baseYaw: Float, basePitch: Float, partialTick: Float,
        camX: Double, camY: Double, camZ: Double
    ): OverridePose? {
        val frames = pathFrames
        if (frames != null) {
            val t = pathTime(time) ?: return null
            val (i, u) = segmentAt(frames, t)
            var yaw = evalComponent(frames, i, u) { it.yaw }.toFloat()
            var pitch = evalComponent(frames, i, u) { it.pitch }.toFloat()
            var x = evalComponent(frames, i, u) { it.x }
            var y = evalComponent(frames, i, u) { it.y }
            var z = evalComponent(frames, i, u) { it.z }

            // 入口淡入（5 tick）：从当前相机位姿滑进首帧，任意起点播放都不瞬跳
            val entry = pathEntry ?: CapturedPose(baseYaw, basePitch, camX, camY, camZ).also { pathEntry = it }
            val entryW = smooth((t / 5.0).coerceIn(0.0, 1.0).toFloat())
            if (entryW < 1f) {
                yaw = Mth.rotLerp(entryW, entry.yaw, yaw)
                pitch = lerp(entryW, entry.pitch, pitch)
                x = entry.x + (x - entry.x) * entryW
                y = entry.y + (y - entry.y) * entryW
                z = entry.z + (z - entry.z) * entryW
            }
            return OverridePose(yaw, pitch, Vec3(x, y, z))
        }

        if (watchEndMs > 0L) {
            if (time >= watchEndMs) {
                val current = overrideCache ?: CapturedPose(baseYaw, basePitch, camX, camY, camZ)
                beginFinish(current, overrideFovCache)
                watchEndMs = 0L
                watchFrom = null
                overrideCache = null
                overrideFovCache = null
                return null
            }
            val from = watchFrom ?: CapturedPose(baseYaw, basePitch, camX, camY, camZ).also { watchFrom = it }
            val w = smooth(
                if (watchSmoothMs <= 0L) 1f
                else ((time - watchStartMs).toFloat() / watchSmoothMs).coerceIn(0f, 1f)
            )
            val px = from.x + (watchX - from.x) * w
            val py = from.y + (watchY - from.y) * w
            val pz = from.z + (watchZ - from.z) * w
            val target = watchLookEntity?.let { Minecraft.getInstance().level?.getEntity(it)?.getEyePosition(partialTick) }
                ?: watchLook
                ?: return null
            val dx = target.x - px
            val dy = target.y - py
            val dz = target.z - pz
            if (dx * dx + dy * dy + dz * dz > 1e-6) {
                val desiredYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
                val desiredPitch = -Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
                val yaw = Mth.rotLerp(w, from.yaw, desiredYaw)
                val pitch = lerp(w, from.pitch, desiredPitch)
                return OverridePose(yaw, pitch, Vec3(px, py, pz))
            }
            return OverridePose(from.yaw, from.pitch, Vec3(px, py, pz))
        }
        return null
    }

    /** getFov 返回值层叠加：path 轨 > 回程过渡 > 手动指令 > vanilla；开镜让位原版变焦。 */
    @JvmStatic
    fun fov(vanilla: Float): Float {
        val player = Minecraft.getInstance().player
        if (player != null && player.isScoping) {
            return vanilla
        }
        val time = now()

        if (finishFov != null) {
            val w = if (FINISH_MS <= 0L) 1f
            else ((time - finishStartMs).toFloat() / FINISH_MS).coerceIn(0f, 1f)
            val value = lerp(smooth(w), finishFov!!, vanilla)
            return value
        }

        val fovFrames = pathFovFrames
        if (fovFrames != null) {
            val frames = pathFrames
            if (frames != null) {
                val elapsedTicks = (time - pathStartMs) * pathSpeed / 50.0
                if (!pathLoop && elapsedTicks >= pathTotal) {
                    // 位姿侧已触发回程；此处跟随 vanilla
                    return vanilla
                }
                val t = if (pathLoop) elapsedTicks % pathTotal else elapsedTicks.coerceAtMost(pathTotal)
                val (i, u) = segmentAt(fovFrames, t)
                val value = evalComponent(fovFrames, i, u) { it.fov!! }.toFloat()
                overrideFovCache = value
                return value
            }
        }

        val w = if (fovTransitionMs <= 0L) 1f
        else ((time - fovStartMs).toFloat() / fovTransitionMs).coerceIn(0f, 1f)
        if (fovReleasing) {
            val start = fovStart ?: vanilla.also { fovStart = it }
            val value = lerp(smooth(w), start, vanilla)
            fovLast = value
            if (w >= 1f) fovReleasing = false
            return value
        }
        val target = fovTarget ?: return vanilla
        val start = fovStart ?: vanilla.also { fovStart = it }
        val value = lerp(smooth(w), start, target)
        fovLast = value
        return value
    }

    private fun lockTarget(partialTick: Float): Vec3? {
        val uuid = lockEntity ?: return Vec3(lockX, lockY, lockZ)
        val entity = Minecraft.getInstance().level?.getEntity(uuid) ?: return null
        return entity.getEyePosition(partialTick)
    }

    private fun smooth(w: Float): Float = w * w * (3f - 2f * w)

    private fun lerp(w: Float, from: Float, to: Float): Float = from + (to - from) * w
}
