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
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.input.InputLockManager

/**
 * MythicMobs 技能机制：在一段时间内压住目标玩家的某几路输入。
 *
 * 用法（MM 技能行内）：
 *   lanternlock{actions=jump,move;time=2000} @Self       自己 2 秒不能移动跳跃
 *   lanternlock{actions=move;time=3500} @PIR{r=16}       对半径内玩家施加硬直
 *   lanternlock{actions=move,turn;time=1200} @Target     连视角一起冻结
 *
 * actions 取值：move（前后左右）/ jump / sneak / turn（视角）/ attack / use，逗号组合。
 * time 是毫秒，通常取「动画时长 + 一点余量」。
 *
 * 受击硬直的常规配法是三层叠加：受击动画 + 本机制 + 防重复施放条件，
 * 三者各管一件事——演出、输入、频率。
 *
 * 目标必须是玩家：输入锁压的是键盘鼠标，生物没有输入可压。
 *
 * 注意：CustomComponentRegistry 通过 (MythicMechanicLoadEvent) 构造器实例化本类，
 * 不能使用内置机制的 (SkillExecutor, File, String, MythicLineConfig) 签名。
 */
@MythicMechanic(
    name = "lanternlock",
    aliases = ["llock"],
    author = "Lantern",
    description = "Locks the target player's movement inputs for a duration"
)
class LanternLockMechanic(
    loader: MythicMechanicLoadEvent
) : SkillMechanic(
    MythicBukkit.inst().skillManager as SkillExecutor,
    loader.container.configLine,
    loader.config
), ITargetedEntitySkill {

    private val lineConfig: MythicLineConfig = loader.config
    private val actions: List<String> = (lineConfig.getString(arrayOf("actions", "a"), "move") ?: "move")
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
    private val durationMs: Long = lineConfig.getInteger(arrayOf("time", "t"), 1000).toLong()

    /**
     * 锁的来源标识。
     *
     * 用机制实例身份而不是技能名：同一份技能文件里写两行 lanternlock 是两个实例，
     * 应当各自记账；同一行反复施放是同一个实例，应当刷新而不是叠成两条
     */
    private val sourceId: String = "mythic-" + System.identityHashCode(this)

    init {
        // 加载期就把动作名拼写错误报出来。留到施放时才静默失效的话，
        // 表现是「技能放了但人照样能跑」，配置者会去怀疑动画、怀疑延迟，
        // 唯独不会怀疑一个拼错的单词
        val unknown = actions.filter { it !in InputLockManager.ACTIONS }
        if (unknown.isNotEmpty()) {
            LanternPlugin.instance.logger.warning(
                "[Lantern] lanternlock 未知动作 ${unknown.joinToString()}（可用: " +
                    "${InputLockManager.ACTIONS.joinToString()}），该行: ${loader.container.configLine}"
            )
        }
    }

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        if (actions.isEmpty() || durationMs <= 0) return SkillResult.INVALID_CONFIG
        val player = target.bukkitEntity as? Player ?: return SkillResult.INVALID_TARGET
        // MM 技能可能在异步线程执行，发包统一调度回主线程；已在主线程则直接执行，
        // 锁表当 tick 落表，换组巡检才看得到 busy
        MythicIntegration.onMainThread {
            InputLockManager.lock(player, "$sourceId-${player.uniqueId}", actions, durationMs)
        }
        return SkillResult.SUCCESS
    }
}
