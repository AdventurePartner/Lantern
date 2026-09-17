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
 * MythicMobs 技能机制：播放/停止/暂停/跳转目标的 Lantern 实体模型动画。
 *
 * 用法（MM 技能行内）：
 *   lanternanim{anim=skill_slash} @Self                        播放（loop，过渡 5 tick）
 *   lanternanim{anim=skill_slash;remove=true;time=0} @Self     停止
 *   lanternanim{anim=roar;mode=once} @PlayersInRadius{r=10}    播一遍
 *   lanternanim{anim=roar;pause=true} @Self                    暂停当前播控
 *   lanternanim{anim=roar;pause=false} @Self                   恢复
 *   lanternanim{anim=roar;seek=1.5} @Self                      时间轴跳到 1.5 秒
 *   lanternanim{anim=slash;layer=combat} @Self                 上身层播放（腿保持移动）
 *   lanternanim{anim=roll;speed=-1} @Self                      倒放
 *
 * layer 取值：motion（缺省，全身层，压住行走）/ combat（上身层，可边跑边放）。
 * speed 支持负值即倒放；绝对值过小（<0.01）视为无效并回退 1.0。
 *
 * 目标可以是实体，也可以是玩家：
 *   实体 —— 自定义名须等于 entityModels.yml 的 key；
 *   玩家 —— 须已分配 host-driven 的 full_body 外观（costumes.yml），
 *           动画名在该外观绑定的动画库里查找。
 * 两者都不满足时客户端忽略该指令。
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
    // 负速度即倒放，底层与协议都支持；此处不能 coerceAtLeast(0.01) 把它钳成正数，
    // 否则 MM 入口永远放不出倒放。只有绝对值过小（无意义）才回退 1.0
    private val speed: Float = lineConfig.getString(arrayOf("speed", "sp"), "1.0").toFloatOrNull()
        ?.takeIf { kotlin.math.abs(it) > 0.01f } ?: 1.0f
    /** 归层：combat = 上身出招层（腿保持移动），缺省全身 */
    private val layer: String? = lineConfig.getString(arrayOf("layer", "l"))
    private val seek: Float? = lineConfig.getString(arrayOf("seek", "sk"))?.toFloatOrNull()
    private val pause: Boolean? = lineConfig.getString(arrayOf("pause", "p"))?.toBooleanStrictOrNull()

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val anim = animation ?: return SkillResult.INVALID_CONFIG
        val entity = target.bukkitEntity ?: return SkillResult.INVALID_TARGET
        // MM 技能可能在异步线程执行，发包统一调度回主线程。
        // 动作优先级：remove=停止 > seek > pause > 播放
        Bukkit.getScheduler().runTask(LanternPlugin.instance, Runnable {
            when {
                remove -> NetworkHandler.stopAnimation(entity, anim, transition)
                seek != null -> NetworkHandler.seekAnimation(entity, anim, seek)
                pause != null ->
                    if (pause) NetworkHandler.pauseAnimation(entity, anim)
                    else NetworkHandler.resumeAnimation(entity, anim)
                else -> NetworkHandler.playAnimation(entity, anim, transition, loop, speed, layer = layer)
            }
        })
        return SkillResult.SUCCESS
    }
}
