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
 * MythicMobs 技能机制：设置目标实体的 Lantern molang 变量（阶段四变量注入）。
 *
 * 用法（MM 技能行内）：
 *   lanternvar{k=charge;v=0.8} @Self          设置变量
 *   lanternvar{k=charge;v=math.sin(...)} @Self 值可以是表达式（客户端求值，可引用 query.*）
 *   lanternvar{k=charge;v=} @Self              空值 = 删除变量
 *
 * 动画文件里的表达式通过 variable.<key> 读取，如
 *   "rotation": ["math.sin(query.anim_time * 360) * variable.charge", 0, 0]
 *
 * 注意：CustomComponentRegistry 通过 (MythicMechanicLoadEvent) 构造器实例化本类（同
 * LanternAnimMechanic 的注册教训），不能用内置机制的 (SkillExecutor, File, String, MythicLineConfig) 签名。
 */
@MythicMechanic(
    name = "lanternvar",
    aliases = ["lvar"],
    author = "Lantern",
    description = "Sets a Lantern molang variable on the target entity"
)
class LanternVarMechanic(
    loader: MythicMechanicLoadEvent
) : SkillMechanic(
    MythicBukkit.inst().skillManager as SkillExecutor,
    loader.container.configLine,
    loader.config
), ITargetedEntitySkill {

    private val key: String? = loader.config.getString(arrayOf("k", "key"))
    private val value: String = loader.config.getString(arrayOf("v", "value"), "")

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val key = key ?: return SkillResult.INVALID_CONFIG
        val entity = target.bukkitEntity ?: return SkillResult.INVALID_TARGET
        // MM 技能可能在异步线程执行，发包统一调度回主线程
        Bukkit.getScheduler().runTask(LanternPlugin.instance, Runnable {
            NetworkHandler.setMolangVariables(entity, mapOf(key to value))
        })
        return SkillResult.SUCCESS
    }
}
