package org.lantern.mythic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
import io.lumine.mythic.core.skills.SkillExecutor
import io.lumine.mythic.core.skills.SkillMechanic
import io.lumine.mythic.core.utils.annotations.MythicMechanic
import org.bukkit.Bukkit
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler

/**
 * MythicMobs 技能机制：播放/停止目标的 Lantern 实体模型动画。
 *
 * 用法（MM 技能行内）：
 *   lanternanim{anim=skill_slash} @Self                        播放（loop，过渡 5 tick）
 *   lanternanim{anim=skill_slash;remove=true;time=0} @Self     停止
 *   lanternanim{anim=roar;mode=once} @PlayersInRadius{r=10}    播一遍
 *
 * 目标实体必须已由 Lantern 实体模型渲染（自定义名 == entityModels.yml 的 key），
 * 否则客户端忽略该指令。remove=false 播放 / remove=true 停止。
 *
 * 注意：CustomComponentRegistry 通过 (MythicMechanicLoadEvent) 构造器实例化本类，
 * 不能使用内置机制的 (SkillExecutor, File, String, MythicLineConfig) 签名。
 */
@MythicMechanic(
    name = "lanternanim",
    aliases = ["lanim"],
    author = "Lantern",
    description = "Plays or stops a Lantern entity-model animation on the target"
)
class LanternAnimMechanic(
    loader: MythicMechanicLoadEvent
) : SkillMechanic(
    MythicBukkit.inst().skillManager as SkillExecutor,
    loader.container.configLine,
    loader.config
), ITargetedEntitySkill {

    private val lineConfig: MythicLineConfig = loader.config
    private val animation: String? = lineConfig.getString(arrayOf("anim", "a"))
    private val remove: Boolean = lineConfig.getBoolean(arrayOf("remove", "r"), false)
    private val transition: Int = lineConfig.getInteger(arrayOf("time", "t"), 5).coerceAtLeast(0)
    private val loop: Boolean = !lineConfig.getString(arrayOf("mode", "m"), "loop").equals("once", ignoreCase = true)
    private val speed: Float = lineConfig.getString(arrayOf("speed", "sp"), "1.0").toFloatOrNull()?.coerceAtLeast(0.01f) ?: 1.0f

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val anim = animation ?: return SkillResult.INVALID_CONFIG
        val entity = target.bukkitEntity ?: return SkillResult.INVALID_TARGET
        // MM 技能可能在异步线程执行，发包统一调度回主线程
        Bukkit.getScheduler().runTask(LanternPlugin.instance, Runnable {
            if (remove) {
                NetworkHandler.stopAnimation(entity, anim, transition)
            } else {
                NetworkHandler.playAnimation(entity, anim, transition, loop, speed)
            }
        })
        return SkillResult.SUCCESS
    }
}
