package org.lantern.command

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.lantern.config.Configurations
import org.lantern.config.UiConfigurations
import org.lantern.handler.CacheHandler
import org.lantern.network.NetworkHandler
import org.lantern.placeholder.PlaceholderService

class LanternCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String?>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "reload", null -> {
                Configurations.load()
                NetworkHandler.invalidateCache()
                PlaceholderService.load(UiConfigurations.getPlaceholderConfigs())
                Bukkit.getOnlinePlayers().forEach { NetworkHandler.sendPackets(it) }
                sender.sendMessage("${ChatColor.DARK_GREEN}Configuration reloaded.")
            }
            "open" -> {
                val screenId = args.getOrNull(1) ?: run {
                    sender.sendMessage("${ChatColor.RED}Usage: /lantern open <screen-id> [player]")
                    return true
                }
                val target: Player = args.getOrNull(2)
                    ?.let { name ->
                        Bukkit.getPlayer(name) ?: run {
                            sender.sendMessage("${ChatColor.RED}Player '$name' not found.")
                            return true
                        }
                    }
                    ?: (sender as? Player) ?: run {
                        sender.sendMessage("${ChatColor.RED}Console must specify a player name.")
                        return true
                    }
                NetworkHandler.sendOpenGui(target, screenId)
                sender.sendMessage("${ChatColor.GREEN}Opened screen '$screenId' for ${target.name}.")
            }
            "give" -> handleGive(sender, args)
            else -> sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use: reload | open | give")
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String?>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("reload", "open", "give")
                .filter { it.startsWith(args[0] ?: "", ignoreCase = true) }
            2 -> when (args[0]?.lowercase()) {
                "open" -> UiConfigurations.getScreens()
                    .mapNotNull { it.get("id")?.asString }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "give" -> CacheHandler.blockModels.keys
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            3 -> if (args[0].equals("open", ignoreCase = true) || args[0].equals("give", ignoreCase = true))
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                 else emptyList()
            else -> emptyList()
        }
    }

    private fun handleGive(sender: CommandSender, args: Array<out String?>): Boolean {
        val blockId = args.getOrNull(1) ?: run {
            sender.sendMessage("${ChatColor.RED}Usage: /lantern give <block-id> [player] [amount]")
            return true
        }
        val cache = CacheHandler.blockModels[blockId] ?: run {
            sender.sendMessage("${ChatColor.RED}Unknown block ID: '$blockId'. Available: ${CacheHandler.blockModels.keys.joinToString()}")
            return true
        }
        val target: Player = args.getOrNull(2)
            ?.let { name ->
                Bukkit.getPlayer(name) ?: run {
                    sender.sendMessage("${ChatColor.RED}Player '$name' not found.")
                    return true
                }
            }
            ?: (sender as? Player) ?: run {
                sender.sendMessage("${ChatColor.RED}Console must specify a player name.")
                return true
            }
        val amount = args.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val item = ItemStack(Material.BARREL, amount).apply {
            val meta = itemMeta ?: return@apply
            if (cache.customModelData > 0) {
                meta.setCustomModelData(cache.customModelData)
            }
            if (cache.displayName.isNotEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', cache.displayName))
            }
            meta.lore = listOf("${ChatColor.DARK_GRAY}lantern:$blockId")
            itemMeta = meta
        }
        target.inventory.addItem(item)
        sender.sendMessage("${ChatColor.GREEN}Gave ${amount}x $blockId to ${target.name}.")
        return true
    }
}
