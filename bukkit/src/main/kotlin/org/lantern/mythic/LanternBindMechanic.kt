package org.lantern.mythic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.INoTargetSkill
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
import org.lantern.bind.BindRegistry

/**
 * MythicMobs 技能机制：把一只实体渲染到另一只实体身上（载体绑定）。
 *
 * 为什么要有这个机制而不是只靠 `cmd{c="lantern bind ..."}`：命令形态要靠
 * `<target.uuid>` 占位符传实体，而占位符只有在这一行确实拿到实体目标时才会被替换。
 * targeter 一旦落空，MM 会把命令按「无目标」执行一次，占位符原样发到服务端，
 * 结果是一条 UUID 解析失败的日志外加绑定没生效。本机制直接拿 AbstractEntity，
 * 不经字符串替换；targeter 落空时走 [cast] 打一条明确的告警，而不是静默失败。
 *
 * 用法一（推荐）：配 type，caster 恒为宿主，载体由本机制自己就近找。
 *   - summon{type=剑气斩击;amount=1;r=0} @self
 *   - lanternbind{type=剑气斩击;radius=8;offset=0,1.2,0;duration=700;delay=1} @self
 *   targeter 用 @self 永远能解析到，载体的查找放在 Lantern 自己手里（按 MM 类型名
 *   筛，取刚召唤出来的那只），整条链不依赖任何 MM 目标器或回调的语义。
 *
 * 用法二：不配 type，由 targeter 给出另一端。
 *   - lanternbind{as=host;offset=0,1.2,0;duration=700} @Target
 *
 * 参数：
 *   type      载体的 MM 类型内部名。配了就由本机制就近查找，caster 恒为宿主
 *   radius    type 查找半径（格），缺省 16
 *   as        不配 type 时生效：follower（缺省）= caster 是载体、target 是宿主；
 *             host = 反过来
 *   offset    x,y,z；rotate=true 时是宿主朝向的局部坐标（x=右 y=上 z=前）
 *   rotate    载体整体 yaw 跟随宿主，缺省 true
 *   visible   宿主脱离视野时载体仍渲染，缺省 false
 *   duration  毫秒，0（缺省）= 直到宿主或载体消失
 *
 * 注意：CustomComponentRegistry 通过 (MythicMechanicLoadEvent) 构造器实例化本类，
 * 不能使用内置机制的 (SkillExecutor, File, String, MythicLineConfig) 签名。
 */
@MythicMechanic(
    name = "lanternbind",
    aliases = ["lbind"],
    author = "Lantern",
    description = "Renders one entity attached to another (client-side follow)"
)
class LanternBindMechanic(
    loader: MythicMechanicLoadEvent
) : SkillMechanic(
    MythicBukkit.inst().skillManager as SkillExecutor,
    loader.container.configLine,
    loader.config
), ITargetedEntitySkill, INoTargetSkill {

    private val lineConfig: MythicLineConfig = loader.config

    /** caster 在这条绑定里的角色：follower（缺省）或 host */
    private val casterIsHost: Boolean =
        lineConfig.getString(arrayOf("as", "role"), "follower").equals("host", ignoreCase = true)

    /** 载体的 MM 类型内部名；配了就由本机制自己找，不靠 targeter 传 */
    private val mobType: String? = lineConfig.getString(arrayOf("type", "mob", "m"))?.takeIf { it.isNotBlank() }

    /** type 查找半径（格） */
    private val searchRadius: Double = lineConfig.getInteger(arrayOf("radius", "r"), 16).toDouble()

    private val offset: DoubleArray = parseOffset(lineConfig.getString(arrayOf("offset", "o"), "0,0,0"))
    // 别名不能再用 r：radius 已经占了，MM 按名字取值不区分目标字段
    private val rotate: Boolean = lineConfig.getBoolean(arrayOf("rotate", "rot"), true)
    private val visible: Boolean = lineConfig.getBoolean(arrayOf("visible", "v"), false)
    private val durationMs: Long = lineConfig.getInteger(arrayOf("duration", "d"), 0).toLong()

    private val configLine: String = loader.container.configLine

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val casterEntity = data.caster?.entity?.bukkitEntity ?: return SkillResult.INVALID_TARGET
        val targetEntity = target.bukkitEntity ?: return SkillResult.INVALID_TARGET

        // MM 技能可能在异步线程执行；查实体表、派发 Bukkit 事件都必须回主线程。
        // 已在主线程则直接执行，不白等一 tick
        MythicIntegration.onMainThread {
            val host: org.bukkit.entity.Entity
            val follower: org.bukkit.entity.Entity
            if (mobType != null) {
                host = casterEntity
                follower = findSummoned(casterEntity, mobType) ?: run {
                    LanternPlugin.instance.logger.warning(
                        "[Lantern] lanternbind 在 ${searchRadius.toInt()} 格内没找到类型 '$mobType' 的载体" +
                            "，绑定未执行（该行: $configLine）"
                    )
                    return@onMainThread
                }
            } else if (casterIsHost) {
                host = casterEntity
                follower = targetEntity
            } else {
                host = targetEntity
                follower = casterEntity
            }
            val failure = BindRegistry.bind(
                host.uniqueId, follower.uniqueId, offset[0], offset[1], offset[2], rotate, visible, durationMs
            )
            if (failure != null) {
                LanternPlugin.instance.logger.warning("[Lantern] lanternbind 绑定失败: $failure（该行: $configLine）")
            }
        }
        return SkillResult.SUCCESS
    }

    /**
     * 就近找刚召唤出来的载体。
     *
     * 按 MM 类型内部名筛，先按与宿主的距离、再按存活 tick 取——summon{r=0} 落在宿主脚下，
     * 距离最近的就是自己召唤的那只。只按存活 tick 取最新的话，两名玩家在 8 格内同一 tick
     * 施放，两只载体的 ticksLived 相同，会互相绑到对方身上
     */
    private fun findSummoned(host: org.bukkit.entity.Entity, type: String): org.bukkit.entity.Entity? {
        val helper = MythicBukkit.inst().apiHelper
        val origin = host.location
        return host.getNearbyEntities(searchRadius, searchRadius, searchRadius)
            .asSequence()
            .filter { it.uniqueId != host.uniqueId }
            .filter { BindRegistry.get(it.uniqueId) == null }
            .filter { helper.getMythicMobInstance(it)?.type?.internalName == type }
            .minWithOrNull(compareBy<org.bukkit.entity.Entity> { it.location.distanceSquared(origin) }.thenBy { it.ticksLived })
    }

    /**
     * targeter 一个实体都没选中时走这里。
     *
     * 不静默返回：绑定失效在画面上表现为「特效没跟着人走」，配置者会去怀疑偏移、
     * 怀疑动画，唯独想不到是 targeter 压根没命中。这条日志把它直接点出来
     */
    override fun cast(data: SkillMetadata): SkillResult {
        LanternPlugin.instance.logger.warning(
            "[Lantern] lanternbind 没有拿到实体目标，绑定未执行——检查 targeter 是否命中" +
                "（该行: $configLine）"
        )
        return SkillResult.CONDITION_FAILED
    }

    private fun parseOffset(raw: String?): DoubleArray {
        val parts = (raw ?: "").split(',')
        return doubleArrayOf(
            parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0,
            parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0,
            parts.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 0.0
        )
    }
}
