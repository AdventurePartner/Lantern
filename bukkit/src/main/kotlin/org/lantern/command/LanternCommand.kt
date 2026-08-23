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
import org.lantern.gui.WardrobeGui
import org.lantern.handler.CostumeAssignmentHandler
import org.lantern.network.NetworkHandler
import org.lantern.placeholder.PlaceholderService

class LanternCommand : CommandExecutor, TabCompleter {
    private val validSlots = setOf("full_body", "back", "tail", "head", "effect")


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
            "costume" -> handleCostume(sender, args)
            "wardrobe" -> handleWardrobe(sender, args)
            else -> sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use: reload | open | give | costume | wardrobe")
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
            1 -> listOf("reload", "open", "give", "costume", "wardrobe")
                .filter { it.startsWith(args[0] ?: "", ignoreCase = true) }
            2 -> when (args[0]?.lowercase()) {
                "open" -> UiConfigurations.getScreens()
                    .mapNotNull { it.get("id")?.asString }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "give" -> CacheHandler.blockModels.keys
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "costume" -> listOf("equip", "unequip")
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "wardrobe" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0]?.lowercase()) {
                "open", "give", "costume" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            4 -> if (args[0].equals("costume", ignoreCase = true))
                    validSlots.filter { it.startsWith(args[3] ?: "", ignoreCase = true) }
                 else emptyList()
            5 -> if (args[0].equals("costume", ignoreCase = true) && args[1]?.lowercase() == "equip")
                    CacheHandler.costumes.entries
                        .filter { it.value.slot.equals(args[3] ?: "", ignoreCase = true) }
                        .map { it.key }
                        .filter { it.startsWith(args[4] ?: "", ignoreCase = true) }
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

    private fun handleCostume(sender: CommandSender, args: Array<out String?>) {
        val sub = args.getOrNull(1)?.lowercase()
        when (sub) {
            "equip" -> {
                val playerName = args.getOrNull(2) ?: run { costumeUsage(sender); return }
                val slot = args.getOrNull(3) ?: run { costumeUsage(sender); return }
                val costumeId = args.getOrNull(4) ?: run { costumeUsage(sender); return }
                val target = Bukkit.getPlayer(playerName) ?: run {
                    sender.sendMessage("${ChatColor.RED}Player '$playerName' not found.")
                    return
                }
                if (slot !in validSlots) {
                    sender.sendMessage("${ChatColor.RED}Invalid slot '$slot'. Valid: ${validSlots.joinToString()}")
                    return
                }
                if (costumeId !in CacheHandler.costumes) {
                    sender.sendMessage("${ChatColor.RED}Unknown costume-id '$costumeId'. Available: ${CacheHandler.costumes.keys.joinToString()}")
                    return
                }
                CostumeAssignmentHandler.assign(target.uniqueId, slot, costumeId)
                CostumeAssignmentHandler.save()
                NetworkHandler.broadcastCostumeAssignment()
                sender.sendMessage("${ChatColor.GREEN}Equipped $costumeId on $slot for ${target.name}.")
            }
            "unequip" -> {
                val playerName = args.getOrNull(2) ?: run { costumeUsage(sender); return }
                val slot = args.getOrNull(3)
                val target = Bukkit.getPlayer(playerName) ?: run {
                    sender.sendMessage("${ChatColor.RED}Player '$playerName' not found.")
                    return
                }
                if (slot != null && slot !in validSlots) {
                    sender.sendMessage("${ChatColor.RED}Invalid slot '$slot'. Valid: ${validSlots.joinToString()}")
                    return
                }
                CostumeAssignmentHandler.remove(target.uniqueId, slot)
                CostumeAssignmentHandler.save()
                NetworkHandler.broadcastCostumeAssignment()
                sender.sendMessage("${ChatColor.GREEN}Unequipped${if (slot != null) " $slot" else " all slots"} for ${target.name}.")
            }
            else -> costumeUsage(sender)
        }
    }

    private fun costumeUsage(sender: CommandSender) {
        sender.sendMessage("${ChatColor.RED}Usage: /lantern costume equip <player> <slot> <costume-id>")
        sender.sendMessage("${ChatColor.RED}       /lantern costume unequip <player> [slot]")
    }

    private fun handleWardrobe(sender: CommandSender, args: Array<out String?>) {
        val target: Player = args.getOrNull(1)
            ?.let { name ->
                Bukkit.getPlayer(name) ?: run {
                    sender.sendMessage("${ChatColor.RED}Player '$name' not found.")
                    return
                }
            }
            ?: (sender as? Player) ?: run {
                sender.sendMessage("${ChatColor.RED}Console must specify a player name.")
                return
            }
        WardrobeGui.openMain(target)
        sender.sendMessage("${ChatColor.GREEN}Opened wardrobe for ${target.name}.")
    }
}
