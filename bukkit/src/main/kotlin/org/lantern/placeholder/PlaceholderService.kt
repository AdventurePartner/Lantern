package org.lantern.placeholder

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler

/**
 * Resolves PlaceholderAPI variables per-player on a repeating schedule
 * and pushes the results to each client via Packet 13.
 *
 * Screens that share the same [ScreenPlaceholderConfig.intervalTicks] are
 * grouped into a single BukkitTask to avoid task proliferation.
 */
object PlaceholderService {

    private val configs = mutableListOf<ScreenPlaceholderConfig>()
    private val taskIds = mutableListOf<Int>()
    private var papiAvailable = false

    /**
     * (Re-)loads placeholder configs and starts the repeating tasks.
     * Must be called on the main thread after [org.lantern.config.UiConfigurations.load].
     */
    fun load(newConfigs: List<ScreenPlaceholderConfig>) {
        stopAll()
        configs.clear()
        configs.addAll(newConfigs)

        papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
        if (!papiAvailable || configs.isEmpty()) return

        // Group by interval — each group gets one sync repeating task
        configs.groupBy { it.intervalTicks }.forEach { (ticks, group) ->
            val taskId = Bukkit.getScheduler().runTaskTimer(
                LanternPlugin.instance,
                Runnable { tickGroup(group) },
                20L,   // 1-second initial delay so Packet 5 arrives first
                ticks
            ).taskId
            taskIds.add(taskId)
        }
    }

    private fun tickGroup(group: List<ScreenPlaceholderConfig>) {
        for (player in Bukkit.getOnlinePlayers()) {
            for (config in group) {
                val values = resolve(player, config)
                if (values.isNotEmpty()) {
                    NetworkHandler.sendPlaceholderUpdate(player, config.screenId, values)
                }
            }
        }
    }

    private fun resolve(player: Player, config: ScreenPlaceholderConfig): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (placeholder in config.placeholders) {
            values[placeholder] = PlaceholderAPI.setPlaceholders(player, placeholder)
        }
        return values
    }

    fun stopAll() {
        taskIds.forEach { Bukkit.getScheduler().cancelTask(it) }
        taskIds.clear()
    }
}
