package org.lantern.mythic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.skills.SkillMechanic
import io.lumine.mythic.core.skills.SkillExecutor
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler

/**
 * lanterncam 相机演出机制（packet 18 下发，目标由 MM targeter 决定，仅对 Player 生效）：
 *
 *   lanterncam{action=shake} @PlayersInRadius{r=10}                 # 震屏（默认）
 *   lanterncam{action=lock;x=0;y=2;z=0} @Self                       # 锁定视角看向目标实体上方 2 格
 *   lanterncam{action=lock;relative=false;x=100;y=64;z=-200} @Self  # 绝对坐标
 *   lanterncam{action=lockentity;smooth=0.5;sync=true} @Self        # 视角跟随目标实体并同步真实朝向
 *   lanterncam{action=fov;value=110;transition=0.5} @Trigger        # 临时 FOV（value 缺省 = 恢复）
 *   lanterncam{action=offset;pitch=10;yaw=15} @Target               # 朝向偏移叠加
 *   lanterncam{action=path;id=boss_intro;speed=1} @PlayersInRadius{r=16}  # 播放打点运镜
 *   lanterncam{action=watch;x=6;y=3;z=6;duration=3} @Self           # 相机到目标旁 6,3,6 观察 3 秒
 *   lanterncam{action=unlock|clear} @Target
 *
 * 注意：CustomComponentRegistry 通过 (MythicMechanicLoadEvent) 构造器实例化本类（同
 * LanternAnimMechanic）；目标选择完全交给 MM targeter，本机制对每个被选中实体逐一生效。
 */
class LanternCamMechanic(
    loader: MythicMechanicLoadEvent
) : SkillMechanic(
    MythicBukkit.inst().skillManager as SkillExecutor,
    loader.container.configLine,
    loader.config
), ITargetedEntitySkill {

    private val lineConfig: MythicLineConfig = loader.config

    private val action: String = lineConfig.getString(arrayOf("action", "a"), "shake")?.lowercase() ?: "shake"
    private val smooth = num(0.3, "smooth", "s").coerceAtLeast(0.0)
    private val duration = num(0.0, "duration", "d").coerceAtLeast(0.0)
    private val sync = lineConfig.getBoolean(arrayOf("sync"), false)
    private val amplitude = num(0.3, "amplitude", "amp")
    private val frequency = num(8.0, "frequency", "freq").coerceAtLeast(0.1)
    private val shakeDuration = num(0.5, "shake-duration", "sdur").coerceAtLeast(0.05)
    private val decay = lineConfig.getBoolean(arrayOf("decay"), true)
    private val fovValue = num(-1.0, "value", "v")
    private val transition = num(1.0, "transition", "t").coerceAtLeast(0.0)
    private val pitch = num(0.0, "pitch", "p")
    private val yaw = num(0.0, "yaw")
    private val roll = num(0.0, "roll", "r")
    private val offX = num(0.0, "x")
    private val offY = num(0.0, "y")
    private val offZ = num(0.0, "z")
    private val relative = lineConfig.getBoolean(arrayOf("relative", "rel"), true)
    private val pathId = lineConfig.getString(arrayOf("id"))
    private val pathSpeed = num(1.0, "speed").coerceIn(0.05, 10.0)
    private val lookX = num(0.0, "lookx", "lx")
    private val lookY = num(0.0, "looky", "ly")
    private val lookZ = num(0.0, "lookz", "lz")
    private val lookAtTarget = lineConfig.getBoolean(arrayOf("lookentity", "look"), true)

    // MythicLineConfig 的 getDouble 重载在各 MM 版本间不稳，统一走字符串解析
    private fun num(def: Double, vararg keys: String): Double =
        keys.firstNotNullOfOrNull { key ->
            lineConfig.getString(arrayOf(key))?.toDoubleOrNull()
        } ?: def

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val player = target.bukkitEntity as? Player ?: return SkillResult.INVALID_TARGET
        // MM 技能可能在异步线程执行，发包统一调度回主线程
        Bukkit.getScheduler().runTask(LanternPlugin.instance, Runnable {
            val base = if (relative) target.bukkitEntity.location else null
            when (action) {
                "lock" -> {
                    // relative=true 时 x/y/z 为相对目标实体的偏移，否则为世界绝对坐标
                    NetworkHandler.cameraLock(
                        player,
                        (base?.x ?: 0.0) + offX,
                        (base?.y ?: 0.0) + offY,
                        (base?.z ?: 0.0) + offZ,
                        smooth, duration, sync
                    )
                }
                "lockentity" -> NetworkHandler.cameraLockEntity(player, target.bukkitEntity.uniqueId, smooth, duration, sync)
                "unlock" -> NetworkHandler.cameraUnlock(player)
                "shake" -> NetworkHandler.cameraShake(player, amplitude, frequency, shakeDuration, decay)
                "fov" -> NetworkHandler.cameraFov(player, fovValue.takeIf { it > 0 }, transition)
                "offset" -> NetworkHandler.cameraOffset(player, pitch, yaw, roll, transition)
                "path" -> {
                    val id = pathId ?: return@Runnable
                    org.lantern.camera.CameraPathService.playTo(player, id, pathSpeed)
                }
                "watch" -> {
                    // 相机位于目标实体 + x/y/z 偏移处，默认看向目标实体本身（lookentity=false
                    // 时看向目标 + lookx/looky/lookz 偏移点）
                    val px = (base?.x ?: 0.0) + offX
                    val py = (base?.y ?: 0.0) + offY
                    val pz = (base?.z ?: 0.0) + offZ
                    if (lookAtTarget) {
                        NetworkHandler.cameraWatch(player, px, py, pz, null, null, null, target.bukkitEntity.uniqueId, duration, smooth)
                    } else {
                        NetworkHandler.cameraWatch(
                            player, px, py, pz,
                            (base?.x ?: 0.0) + lookX, (base?.y ?: 0.0) + lookY, (base?.z ?: 0.0) + lookZ,
                            null, duration, smooth
                        )
                    }
                }
                "clear" -> NetworkHandler.cameraClear(player)
            }
        })
        return SkillResult.SUCCESS
    }
}
