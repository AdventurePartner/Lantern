package org.lantern.command

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.LivingEntity
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
                org.lantern.animation.AnimationOrchestrator.load(org.lantern.LanternPlugin.instance)
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
            "anim" -> handleAnim(sender, args)
            "var" -> handleVar(sender, args)
            "costume" -> handleCostume(sender, args)
            "wardrobe" -> handleWardrobe(sender, args)
            else -> sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use: reload | open | give | anim | var | costume | wardrobe")
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
            1 -> listOf("reload", "open", "give", "anim", "var", "costume", "wardrobe")
                .filter { it.startsWith(args[0] ?: "", ignoreCase = true) }
            2 -> when (args[0]?.lowercase()) {
                "open" -> UiConfigurations.getScreens()
                    .mapNotNull { it.get("id")?.asString }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "give" -> CacheHandler.blockModels.keys
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "anim" -> listOf("play", "stop")
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "costume" -> listOf("equip", "unequip")
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "wardrobe" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when {
                args[0].equals("costume", ignoreCase = true) ->
                    validSlots.filter { it.startsWith(args[3] ?: "", ignoreCase = true) }
                args[0].equals("anim", ignoreCase = true) && args[1]?.lowercase() == "play" ->
                    listOf("0", "5", "10").filter { it.startsWith(args[3] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0]?.lowercase()) {
                "open", "give", "costume" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            5 -> when {
                args[0].equals("costume", ignoreCase = true) && args[1]?.lowercase() == "equip" ->
                    CacheHandler.costumes.entries
                        .filter { it.value.slot.equals(args[3] ?: "", ignoreCase = true) }
                        .map { it.key }
                        .filter { it.startsWith(args[4] ?: "", ignoreCase = true) }
                args[0].equals("anim", ignoreCase = true) && args[1]?.lowercase() == "play" ->
                    listOf("loop", "once").filter { it.startsWith(args[4] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
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

    private fun handleAnim(sender: CommandSender, args: Array<out String?>) {
        val sub = args.getOrNull(1)?.lowercase()
        val animation = args.getOrNull(2)
        if (sub != "play" && sub != "stop" || animation == null) {
            animUsage(sender)
            return
        }
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}Console cannot target nearby entities; use the NetworkHandler API instead.")
            return
        }
        val transition = (args.getOrNull(3)?.toIntOrNull() ?: if (sub == "play") 5 else 0).coerceAtLeast(0)
        val loop = !args.getOrNull(4).equals("once", ignoreCase = true)
        val speed = args.getOrNull(5)?.toFloatOrNull()?.takeIf { it > 0.01f } ?: 1.0f
        val target = findAnimTarget(player) ?: run {
            sender.sendMessage("${ChatColor.RED}No named entity within 8 blocks.")
            return
        }
        val displayName = target.customName?.let { " (${ChatColor.stripColor(it)})" } ?: ""
        if (sub == "play") {
            NetworkHandler.playAnimation(target, animation, transition, loop, speed)
            sender.sendMessage("${ChatColor.GREEN}Playing '$animation' (${if (loop) "loop" else "once"} x$speed) on ${target.type}$displayName.")
        } else {
            NetworkHandler.stopAnimation(target, animation, transition)
            sender.sendMessage("${ChatColor.GREEN}Stopped '$animation' on ${target.type}$displayName.")
        }
    }

    /** /lantern var <key> <value|del>：设置/删除 8 格内最近已命名实体的 molang 变量 */
    private fun handleVar(sender: CommandSender, args: Array<out String?>) {
        val key = args.getOrNull(1)
        val value = args.getOrNull(2)
        if (key == null || value == null) {
            sender.sendMessage("${ChatColor.RED}Usage: /lantern var <key> <value|del>")
            sender.sendMessage("${ChatColor.RED}       value 支持数字或 molang 表达式（如 math.sin(query.time_stamp/100)）")
            return
        }
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}Console cannot target nearby entities; use the NetworkHandler API instead.")
            return
        }
        val target = findAnimTarget(player) ?: run {
            sender.sendMessage("${ChatColor.RED}No named entity within 8 blocks.")
            return
        }
        if (value.equals("del", ignoreCase = true)) {
            NetworkHandler.setMolangVariables(target, mapOf(key to ""))
            sender.sendMessage("${ChatColor.GREEN}Removed molang variable '$key' from nearby entity.")
        } else {
            NetworkHandler.setMolangVariables(target, mapOf(key to value))
            sender.sendMessage("${ChatColor.GREEN}Set '$key=$value' on nearby entity.")
        }
    }

    /** 最近的带自定义名字的生物（Lantern 实体模型按自定义名匹配），8 格范围内。 */
    private fun findAnimTarget(player: Player): LivingEntity? =
        player.getNearbyEntities(8.0, 8.0, 8.0)
            .filterIsInstance<LivingEntity>()
            .filter { it.customName != null }
            .minByOrNull { it.location.distanceSquared(player.location) }

    private fun animUsage(sender: CommandSender) {
        sender.sendMessage("${ChatColor.RED}Usage: /lantern anim play <animation> [transition-ticks] [loop|once]")
        sender.sendMessage("${ChatColor.RED}       /lantern anim stop <animation> [transition-ticks]")
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
