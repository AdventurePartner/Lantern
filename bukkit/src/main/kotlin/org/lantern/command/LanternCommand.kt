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
            "cam" -> handleCam(sender, args)
            "campath" -> handleCampath(sender, args)
            "costume" -> handleCostume(sender, args)
            "wardrobe" -> handleWardrobe(sender, args)
            else -> sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use: reload | open | give | anim | var | cam | campath | costume | wardrobe")
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
            1 -> listOf("reload", "open", "give", "anim", "var", "cam", "costume", "wardrobe")
                .filter { it.startsWith(args[0] ?: "", ignoreCase = true) }
            2 -> when (args[0]?.lowercase()) {
                "open" -> UiConfigurations.getScreens()
                    .mapNotNull { it.get("id")?.asString }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "give" -> CacheHandler.blockModels.keys
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "anim" -> listOf("play", "stop", "pause", "resume", "seek")
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "cam" -> listOf("info", "toggle", "reset", "set", "lock", "lockentity", "unlock", "shake", "fov", "offset", "path", "watch", "clear")
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "campath" -> listOf("start", "add", "undo", "clear", "list", "preview", "play", "save", "stop")
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
                args[0].equals("cam", ignoreCase = true) && args[1]?.lowercase() == "set" ->
                    Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.startsWith(args[3] ?: "", ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0]?.lowercase()) {
                "open", "give", "costume" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                "cam" -> when (args[1]?.lowercase()) {
                    "set" -> listOf("offset-x", "offset-y", "distance")
                        .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                    "path" -> org.lantern.camera.CameraPathService.savedIds()
                        .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                    "lock", "watch" -> emptyList()
                    else -> Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                }
                "campath" -> when (args[1]?.lowercase()) {
                    "start" -> emptyList()
                    "add" -> listOf("linear", "smooth", "hold")
                        .filter { it.startsWith(args[3] ?: "", ignoreCase = true) }
                    else -> emptyList()
                }
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
        if (sub !in setOf("play", "stop", "pause", "resume", "seek") || animation == null) {
            animUsage(sender)
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
        val displayName = target.customName?.let { " (${ChatColor.stripColor(it)})" } ?: ""
        when (sub) {
            "play" -> {
                val transition = (args.getOrNull(3)?.toIntOrNull() ?: 5).coerceAtLeast(0)
                val loop = !args.getOrNull(4).equals("once", ignoreCase = true)
                val speed = args.getOrNull(5)?.toFloatOrNull()?.takeIf { it > 0.01f } ?: 1.0f
                NetworkHandler.playAnimation(target, animation, transition, loop, speed)
                sender.sendMessage("${ChatColor.GREEN}Playing '$animation' (${if (loop) "loop" else "once"} x$speed) on ${target.type}$displayName.")
            }
            "stop" -> {
                val transition = (args.getOrNull(3)?.toIntOrNull() ?: 0).coerceAtLeast(0)
                NetworkHandler.stopAnimation(target, animation, transition)
                sender.sendMessage("${ChatColor.GREEN}Stopped '$animation' on ${target.type}$displayName.")
            }
            "pause" -> {
                NetworkHandler.pauseAnimation(target, animation)
                sender.sendMessage("${ChatColor.GREEN}Paused '$animation' on ${target.type}$displayName.")
            }
            "resume" -> {
                NetworkHandler.resumeAnimation(target, animation)
                sender.sendMessage("${ChatColor.GREEN}Resumed '$animation' on ${target.type}$displayName.")
            }
            "seek" -> {
                val seconds = args.getOrNull(3)?.toFloatOrNull()?.takeIf { it >= 0f } ?: run {
                    sender.sendMessage("${ChatColor.RED}Usage: /lantern anim seek <animation> <seconds>")
                    return
                }
                NetworkHandler.seekAnimation(target, animation, seconds)
                sender.sendMessage("${ChatColor.GREEN}Seeked '$animation' to ${seconds}s on ${target.type}$displayName.")
            }
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
        sender.sendMessage("${ChatColor.RED}Usage: /lantern anim play <animation> [transition-ticks] [loop|once] [speed]")
        sender.sendMessage("${ChatColor.RED}       /lantern anim stop <animation> [transition-ticks]")
        sender.sendMessage("${ChatColor.RED}       /lantern anim pause <animation> | resume <animation>")
        sender.sendMessage("${ChatColor.RED}       /lantern anim seek <animation> <seconds>")
    }

    /** /lantern cam：越肩相机管理（camera.yml 配置，方向键微调之外的管理入口）。 */
    private fun handleCam(sender: CommandSender, args: Array<out String?>) {
        val service = org.lantern.camera.ShoulderCameraService
        when (args.getOrNull(1)?.lowercase() ?: "info") {
            "info" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                val state = service.stateOf(target)
                sender.sendMessage(
                    "${ChatColor.GOLD}${target.name}: shoulder ${if (state.enabled) "ON" else "OFF"}, " +
                        "offset-x=${state.offsetX}, offset-y=${state.offsetY}, distance=${state.distance} " +
                        "(server ${if (service.globalEnabled()) "enabled" else "disabled"})"
                )
            }
            "toggle" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                if (service.toggle(target) == null) {
                    sender.sendMessage("${ChatColor.RED}Shoulder camera is disabled server-wide (camera.yml shoulder.enabled).")
                } else {
                    val state = service.stateOf(target)
                    sender.sendMessage("${ChatColor.GREEN}Shoulder camera ${if (state.enabled) "enabled" else "disabled"} for ${target.name}.")
                }
            }
            "reset" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                service.reset(target)
                val state = service.stateOf(target)
                sender.sendMessage(
                    "${ChatColor.GREEN}Reset shoulder camera for ${target.name} " +
                        "(offset-x=${state.offsetX}, offset-y=${state.offsetY}, distance=${state.distance})."
                )
            }
            "set" -> {
                val param = args.getOrNull(2)?.lowercase() ?: run {
                    camUsage(sender)
                    return
                }
                val value = args.getOrNull(3)?.toDoubleOrNull() ?: run {
                    camUsage(sender)
                    return
                }
                if (param !in setOf("offset-x", "offset-y", "distance")) {
                    camUsage(sender)
                    return
                }
                val target = camTarget(sender, args.getOrNull(4)) ?: return
                if (!service.setParam(target, param, value)) {
                    camUsage(sender)
                    return
                }
                val applied = when (param) {
                    "offset-x" -> service.stateOf(target).offsetX
                    "offset-y" -> service.stateOf(target).offsetY
                    else -> service.stateOf(target).distance
                }
                sender.sendMessage("${ChatColor.GREEN}Set $param=$value (clamped to $applied) for ${target.name}.")
            }
            // ============ 演出指令（阶段二） ============
            "lock" -> {
                val x = args.getOrNull(2)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val y = args.getOrNull(3)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val z = args.getOrNull(4)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val tail = parseCamTail(args, 5)
                val target = camTarget(sender, tail.playerName) ?: return
                NetworkHandler.cameraLock(
                    target, x, y, z,
                    tail.numbers.getOrElse(0) { 0.3 },
                    tail.numbers.getOrElse(1) { 0.0 },
                    tail.sync
                )
                sender.sendMessage("${ChatColor.GREEN}Locking ${target.name}'s camera to ($x, $y, $z).")
            }
            "lockentity" -> {
                val tail = parseCamTail(args, 2)
                val target = camTarget(sender, tail.playerName) ?: return
                val origin = sender as? Player ?: target
                val entity = findAnimTarget(origin) ?: run {
                    sender.sendMessage("${ChatColor.RED}No named entity within 8 blocks of ${origin.name}.")
                    return
                }
                NetworkHandler.cameraLockEntity(
                    target, entity.uniqueId,
                    tail.numbers.getOrElse(0) { 0.3 },
                    tail.numbers.getOrElse(1) { 0.0 },
                    tail.sync
                )
                sender.sendMessage("${ChatColor.GREEN}Locking ${target.name}'s camera onto ${entity.type}.")
            }
            "unlock" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                NetworkHandler.cameraUnlock(target)
                sender.sendMessage("${ChatColor.GREEN}Unlocked ${target.name}'s camera.")
            }
            "shake" -> {
                val tail = parseCamTail(args, 2)
                val target = camTarget(sender, tail.playerName) ?: return
                NetworkHandler.cameraShake(
                    target,
                    tail.numbers.getOrElse(0) { 0.3 },
                    tail.numbers.getOrElse(1) { 8.0 },
                    tail.numbers.getOrElse(2) { 0.5 }
                )
                sender.sendMessage("${ChatColor.GREEN}Shaking ${target.name}'s camera.")
            }
            "fov" -> {
                val tail = parseCamTail(args, 2)
                val target = camTarget(sender, tail.playerName) ?: return
                NetworkHandler.cameraFov(target, tail.numbers.getOrNull(0), tail.numbers.getOrElse(1) { 1.0 })
                sender.sendMessage("${ChatColor.GREEN}Camera fov of ${target.name}: ${tail.numbers.getOrNull(0) ?: "restore"}.")
            }
            "offset" -> {
                val tail = parseCamTail(args, 2)
                val pitch = tail.numbers.getOrNull(0) ?: run { camUsage(sender); return }
                val yaw = tail.numbers.getOrNull(1) ?: run { camUsage(sender); return }
                val target = camTarget(sender, tail.playerName) ?: return
                NetworkHandler.cameraOffset(
                    target, pitch, yaw,
                    tail.numbers.getOrElse(2) { 0.0 },
                    tail.numbers.getOrElse(3) { 2.0 }
                )
                sender.sendMessage("${ChatColor.GREEN}Camera offset for ${target.name}: pitch=$pitch yaw=$yaw.")
            }
            "path" -> {
                val id = args.getOrNull(2) ?: run { camUsage(sender); return }
                val tail = parseCamTail(args, 3)
                val target = camTarget(sender, tail.playerName) ?: return
                val speed = tail.numbers.getOrElse(0) { 1.0 }
                val error = org.lantern.camera.CameraPathService.playTo(target, id, speed)
                if (error != null) {
                    sender.sendMessage("${ChatColor.RED}$error")
                } else {
                    sender.sendMessage("${ChatColor.GREEN}Playing camera path '$id' for ${target.name} (speed=$speed).")
                }
            }
            "watch" -> {
                val x = args.getOrNull(2)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val y = args.getOrNull(3)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val z = args.getOrNull(4)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val lx = args.getOrNull(5)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val ly = args.getOrNull(6)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val lz = args.getOrNull(7)?.toDoubleOrNull() ?: run { camUsage(sender); return }
                val tail = parseCamTail(args, 8)
                val target = camTarget(sender, tail.playerName) ?: return
                NetworkHandler.cameraWatch(
                    target, x, y, z, lx, ly, lz, null,
                    tail.numbers.getOrElse(0) { 0.0 },
                    tail.numbers.getOrElse(1) { 1.0 }
                )
                sender.sendMessage("${ChatColor.GREEN}Watch point ($x, $y, $z) looking at ($lx, $ly, $lz) for ${target.name}.")
            }
            "clear" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                NetworkHandler.cameraClear(target)
                sender.sendMessage("${ChatColor.GREEN}Cleared camera fx for ${target.name}.")
            }
            else -> camUsage(sender)
        }
    }

    /** /lantern campath：客户端打点编辑运镜路径（会话在服务端内存，save 落盘）。 */
    private fun handleCampath(sender: CommandSender, args: Array<out String?>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}Camera path authoring requires an in-game player (use /lantern cam path <id> [player] to play).")
            return
        }
        val service = org.lantern.camera.CameraPathService
        val message: String = when (args.getOrNull(1)?.lowercase()) {
            "start" -> {
                val id = args.getOrNull(2) ?: run {
                    sender.sendMessage("${ChatColor.RED}Usage: /lantern campath start <id>")
                    return
                }
                service.start(player, id)
            }
            "add" -> service.add(player, listOf(args.getOrNull(2), args.getOrNull(3), args.getOrNull(4)))
            "undo" -> service.undo(player)
            "clear" -> service.clear(player)
            "list" -> {
                service.list(player).forEach { sender.sendMessage("${ChatColor.GOLD}$it") }
                return
            }
            "preview" -> service.preview(player)
            "play" -> service.playSelf(player, args.getOrNull(2)?.toDoubleOrNull() ?: 1.0)
            "save" -> service.save(player)
            "stop" -> service.stopEditing(player)
            else -> run {
                campathUsage(sender)
                return
            }
        }
        sender.sendMessage("${ChatColor.GREEN}$message")
    }

    private fun campathUsage(sender: CommandSender) {
        sender.sendMessage("${ChatColor.RED}Usage: /lantern campath start <id> | stop")
        sender.sendMessage("${ChatColor.RED}       /lantern campath add [t-ticks] [linear|smooth|hold] [fov]")
        sender.sendMessage("${ChatColor.RED}       /lantern campath undo | clear | list | preview")
        sender.sendMessage("${ChatColor.RED}       /lantern campath play [speed] | save")
    }

    /** cam 子命令的目标玩家：指定名取在线玩家，否则执行者，控制台必须指定。 */
    private fun camTarget(sender: CommandSender, name: String?): Player? {
        if (name != null) {
            val target = Bukkit.getPlayer(name)
            if (target == null) sender.sendMessage("${ChatColor.RED}Player '$name' not found.")
            return target
        }
        val self = sender as? Player
        if (self == null) sender.sendMessage("${ChatColor.RED}Console must specify a player name.")
        return self
    }

    /** 演出子命令的尾参解析：数字槽按出现顺序填充，"sync" 置位，其余首个非数字视为玩家名。 */
    private class CamTail(val numbers: List<Double>, val sync: Boolean, val playerName: String?)

    private fun parseCamTail(args: Array<out String?>, from: Int): CamTail {
        val numbers = mutableListOf<Double>()
        var sync = false
        var playerName: String? = null
        for (i in from until args.size) {
            val token = args[i]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val number = token.toDoubleOrNull()
            when {
                number != null -> numbers.add(number)
                token.equals("sync", ignoreCase = true) -> sync = true
                playerName == null -> playerName = token
            }
        }
        return CamTail(numbers, sync, playerName)
    }

    private fun camUsage(sender: CommandSender) {
        sender.sendMessage("${ChatColor.RED}Usage: /lantern cam info [player] | toggle [player] | reset [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam set <offset-x|offset-y|distance> <value> [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam lock <x> <y> <z> [smooth] [duration] [sync] [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam lockentity [smooth] [duration] [sync] [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam unlock [player] | clear [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam shake [amplitude] [frequency] [duration] [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam fov [degrees] [transition-sec] [player]  (no value = restore)")
        sender.sendMessage("${ChatColor.RED}       /lantern cam offset <pitch> <yaw> [roll] [transition-sec] [player]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam path <id> [player] [speed]")
        sender.sendMessage("${ChatColor.RED}       /lantern cam watch <x> <y> <z> <look-x> <look-y> <look-z> [duration] [smooth] [player]")
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
