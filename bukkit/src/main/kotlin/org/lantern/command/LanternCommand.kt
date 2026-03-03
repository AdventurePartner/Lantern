package org.lantern.command

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.lantern.config.Configurations
import org.lantern.config.UiConfigurations
import org.lantern.network.NetworkHandler

class LanternCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String?>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "reload", null -> {
                Configurations.load()
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
            else -> sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use: reload | open <screen-id> [player]")
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
            1 -> listOf("reload", "open")
                .filter { it.startsWith(args[0] ?: "", ignoreCase = true) }
            2 -> if (args[0].equals("open", ignoreCase = true))
                    UiConfigurations.getScreens()
                        .mapNotNull { it.get("id")?.asString }
                        .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                 else emptyList()
            3 -> if (args[0].equals("open", ignoreCase = true))
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                 else emptyList()
            else -> emptyList()
        }
    }
}
