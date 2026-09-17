package org.lantern.animation

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.lantern.LanternPlugin
import org.lantern.handler.CacheHandler
import org.lantern.handler.CostumeAssignmentHandler
import org.lantern.network.NetworkHandler
import org.lantern.util.TextUtil.colorify

/**
 * 动画组按条件热切换：按持有物、权限等条件给玩家换整套动作。
 *
 * 一个「动画组」就是一套外观（costume）——外观本身已经绑定了模型、动画库、
 * 状态表与连招，所以切组即切外观，不需要另造一层概念。持大剑换一套待机行走攻击，
 * 换回空手再换回来，全部靠这张表驱动。
 *
 * 判定放服务端：条件涉及物品 lore、权限这些客户端拿不全的数据；切换频率是
 * 秒级（周期检查 + 仅变化时下发），不构成广播压力。
 *
 * animationGroups.yml 写法（多条按 priority 降序匹配，第一个命中的生效，
 * 都不命中则回落 costumes.yml 的 player-default.costume）：
 *
 *   重型武器:
 *     costume: player_heavy        # 必须是 costumes.yml 里真实存在的条目
 *     priority: 20
 *     condition:                   # 同一条内的条件全部满足才算命中
 *       item-lore-contains: "重型"   # 主手物品 lore 任意一行包含（去色号后比较）
 *       # item-name-contains: "巨剑"  # 主手物品显示名包含
 *       # item-type: DIAMOND_SWORD   # 主手物品类型
 *       # permission: "lantern.heavy" # 玩家权限节点
 *
 * 换组即换整套：模型、动画库、状态表、连招（连招按 costume 限定，见
 * playerActions.yml）全部跟着走。指向不存在的外观会在加载期打警告。
 */
object AnimationGroupService {

    /**
     * 一条分组规则。条件全部满足才命中，多条规则按 priority 降序匹配，
     * 第一个命中的生效；都不命中则回落服务端默认外观
     */
    private class Rule(
        val id: String,
        val costume: String,
        val priority: Int,
        val itemNameContains: String?,
        val itemLoreContains: String?,
        val itemType: String?,
        val permission: String?
    ) {
        fun matches(player: Player): Boolean {
            if (permission != null && !player.hasPermission(permission)) return false
            if (itemNameContains == null && itemLoreContains == null && itemType == null) return true

            val stack = player.inventory.itemInMainHand
            if (stack.type.isAir) return false
            if (itemType != null && !stack.type.name.equals(itemType, ignoreCase = true)) return false

            val meta = stack.itemMeta
            if (itemNameContains != null) {
                val name = meta?.displayName ?: return false
                if (!org.bukkit.ChatColor.stripColor(name)!!.contains(itemNameContains)) return false
            }
            if (itemLoreContains != null) {
                val lore = meta?.lore ?: return false
                val hit = lore.any {
                    org.bukkit.ChatColor.stripColor(it)?.contains(itemLoreContains) == true
                }
                if (!hit) return false
            }
            return true
        }
    }

    private var rules: List<Rule> = emptyList()
    private var task: BukkitTask? = null

    /** 玩家当前生效的组，避免每轮重复下发 */
    private val current = ConcurrentHashMap<UUID, String>()

    fun load(plugin: LanternPlugin) {
        val file = File(plugin.dataFolder, "animationGroups.yml")
        if (!file.exists()) {
            runCatching { plugin.saveResource("animationGroups.yml", false) }
        }
        if (!file.exists()) {
            rules = emptyList()
            return
        }
        val config = YamlConfiguration.loadConfiguration(file)
        val parsed = ArrayList<Rule>()
        for (id in config.getKeys(false)) {
            val section = config.getConfigurationSection(id) ?: continue
            val costume = section.getString("costume")?.takeIf { it.isNotBlank() } ?: continue
            parsed.add(
                Rule(
                    id = id,
                    costume = costume,
                    priority = section.getInt("priority", 0),
                    itemNameContains = section.getString("condition.item-name-contains")
                        ?.takeIf { it.isNotBlank() }?.colorify()
                        ?.let { org.bukkit.ChatColor.stripColor(it) },
                    itemLoreContains = section.getString("condition.item-lore-contains")
                        ?.takeIf { it.isNotBlank() }?.colorify()
                        ?.let { org.bukkit.ChatColor.stripColor(it) },
                    itemType = section.getString("condition.item-type")?.takeIf { it.isNotBlank() },
                    permission = section.getString("condition.permission")?.takeIf { it.isNotBlank() }
                )
            )
        }
        rules = parsed.sortedByDescending { it.priority }
        current.clear()

        // 加载期校验：规则指向不存在的外观是最常见的配置错误。运行期静默跳过
        // 会让人完全无从排查（改了配置、重载了、就是不生效），必须在这里点名
        val known = CacheHandler.costumes.keys
        rules.forEach { rule ->
            if (rule.costume !in known) {
                plugin.logger.warning(
                    "[Lantern] 动画组 '${rule.id}' 指向的外观 '${rule.costume}' 不在 costumes.yml 中" +
                        "（现有外观: ${known.joinToString(", ").ifEmpty { "无" }}），该规则不会生效"
                )
            }
        }
        if (rules.isNotEmpty()) {
            plugin.logger.info("[Lantern] 已加载 ${rules.size} 条动画组规则")
        }
    }

    /** 周期检查：1 秒一轮足够跟手，且远低于每 tick 遍历的开销 */
    fun start(plugin: LanternPlugin) {
        stop()
        if (rules.isEmpty()) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun forget(playerUUID: UUID) {
        current.remove(playerUUID)
    }

    private fun tick() {
        if (rules.isEmpty()) return
        Bukkit.getOnlinePlayers().forEach { player ->
            val target = rules.firstOrNull { it.matches(player) }?.costume
                ?: CacheHandler.defaultPlayerCostume
                ?: return@forEach
            if (current[player.uniqueId] == target) return@forEach
            if (CacheHandler.costumes[target] == null) return@forEach

            current[player.uniqueId] = target
            CostumeAssignmentHandler.assign(player.uniqueId, "full_body", target)
            // 外观分配是全员可见状态（别人也要看到你换了模型），沿用既有广播
            NetworkHandler.sendCostumeAssignmentToAll(player.uniqueId, target)
        }
    }
}
