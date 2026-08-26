package org.lantern.camera.control

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
 * 相机演出指令状态机（packet 18：lock / unlock / shake / fov / offset / clear）。
 *
 * 全部状态只在渲染线程触碰（NetworkParser 经 Minecraft.execute 派发，与 Camera.setup /
 * GameRenderer.getFov 同线程）。应用顺序（策划 §5.3）：
 * 越肩（CameraMixin redirect）→ offset（朝向叠加）→ lock（朝向覆写，带渐入权重）→ shake（位置扰动）。
 * lock 默认渲染层无痕；sync=true 时同步写回玩家真实朝向（服务端可见）。
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

    // ---- fov：临时视场角（getFov 返回值层，不动 Options.fov）----
    private var fovTarget: Float? = null
    private var fovStart: Float? = null
    private var fovLast = 0f
    private var fovStartMs = 0L
    private var fovTransitionMs = 0L
    private var fovReleasing = false

    data class Pose(
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
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
            "clear" -> clear()
        }
    }

    /** 演出状态清空：offset/fov 快速归零过渡，lock/shake 立即停（不影响越肩）。 */
    fun clear() {
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
    }

    /**
     * 是否有影响朝向的演出状态激活（offset 过渡中/非零，或 lock 生效中）。
     * 供拾取修正门控：一人称下视图被旋转时，射线也要跟准星。
     */
    @JvmStatic
    fun orientationActive(): Boolean {
        val time = now()
        val offsetEngaged = time < offsetStartMs + offsetTransitionMs ||
            offsetTargetPitch != 0f || offsetTargetYaw != 0f || offsetTargetRoll != 0f ||
            offsetPitch != 0f || offsetYaw != 0f || offsetRoll != 0f
        return offsetEngaged || lockEndMs > time
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
        var engaged = false

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

        // shake：位置扰动（世界 Y + 视线水平右向量），默认线性衰减
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
        return Pose(yaw, pitch, roll, rightX * sx, sy.toDouble(), rightZ * sx)
    }

    /** getFov 返回值层叠加：无激活时原样返回 vanilla 值；开镜（望远镜）期间让位给原版变焦。 */
    @JvmStatic
    fun fov(vanilla: Float): Float {
        val player = Minecraft.getInstance().player
        if (player != null && player.isScoping) {
            return vanilla
        }
        val time = now()
        val w = if (fovTransitionMs <= 0L) 1f
        else ((time - fovStartMs).toFloat() / fovTransitionMs).coerceIn(0f, 1f)
        if (fovReleasing) {
            val start = fovStart ?: vanilla.also { fovStart = it }
            val value = lerp(smooth(w), start, vanilla)
            fovLast = value
            if (w >= 1f) {
                // 收尾必须同时清掉目标，否则下一帧 fovTarget 仍非空会跳回目标值
                fovReleasing = false
                fovTarget = null
                fovStart = null
            }
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
