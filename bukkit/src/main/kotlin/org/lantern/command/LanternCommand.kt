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
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent

class LanternCommand : CommandExecutor, TabCompleter {
    private val validSlots = setOf("full_body", "back", "tail", "head", "effect")


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String?>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "reload" -> {
                Configurations.load()
                org.lantern.animation.AnimationOrchestrator.load(org.lantern.LanternPlugin.instance)
                NetworkHandler.invalidateCache()
                PlaceholderService.load(UiConfigurations.getPlaceholderConfigs())
                Bukkit.getOnlinePlayers().forEach { NetworkHandler.sendPackets(it) }
                sender.sendMessage("${ChatColor.DARK_GREEN}配置已重载。")
            }
            "open" -> {
                val screenId = args.getOrNull(1) ?: run {
                    sender.sendMessage("${ChatColor.RED}用法: /lantern open <界面ID> [玩家]")
                    return true
                }
                val target: Player = args.getOrNull(2)
                    ?.let { name ->
                        Bukkit.getPlayer(name) ?: run {
                            sender.sendMessage("${ChatColor.RED}玩家 '$name' 不存在。")
                            return true
                        }
                    }
                    ?: (sender as? Player) ?: run {
                        sender.sendMessage("${ChatColor.RED}控制台必须指定玩家名。")
                        return true
                    }
                NetworkHandler.sendOpenGui(target, screenId)
                sender.sendMessage("${ChatColor.GREEN}已为 ${target.name} 打开界面 '$screenId'。")
            }
            "give" -> handleGive(sender, args)
            "anim" -> handleAnim(sender, args)
            "var" -> handleVar(sender, args)
            "cam" -> handleCam(sender, args)
            "campath" -> handleCampath(sender, args)
            "costume" -> handleCostume(sender, args)
            "wardrobe" -> handleWardrobe(sender, args)
            "help" -> showHelp(sender)
            null -> showHelp(sender)
            else -> {
                sender.sendMessage("${ChatColor.RED}未知子命令，输入 /lantern help 查看全部命令。")
                showHelp(sender)
            }
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
            1 -> listOf("reload", "open", "give", "anim", "var", "cam", "campath", "costume", "wardrobe", "help")
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
            sender.sendMessage("${ChatColor.RED}用法: /lantern give <方块ID> [玩家] [数量]")
            return true
        }
        val cache = CacheHandler.blockModels[blockId] ?: run {
            sender.sendMessage("${ChatColor.RED}未知方块 ID: '$blockId'。可用: ${CacheHandler.blockModels.keys.joinToString()}")
            return true
        }
        val target: Player = args.getOrNull(2)
            ?.let { name ->
                Bukkit.getPlayer(name) ?: run {
                    sender.sendMessage("${ChatColor.RED}玩家 '$name' 不存在。")
                    return true
                }
            }
            ?: (sender as? Player) ?: run {
                sender.sendMessage("${ChatColor.RED}控制台必须指定玩家名。")
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
        sender.sendMessage("${ChatColor.GREEN}已给予 ${target.name} ${amount}x $blockId。")
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
                    sender.sendMessage("${ChatColor.RED}玩家 '$playerName' 不存在。")
                    return
                }
                if (slot !in validSlots) {
                    sender.sendMessage("${ChatColor.RED}无效槽位 '$slot'。可用: ${validSlots.joinToString()}")
                    return
                }
                if (costumeId !in CacheHandler.costumes) {
                    sender.sendMessage("${ChatColor.RED}未知装扮 ID '$costumeId'。可用: ${CacheHandler.costumes.keys.joinToString()}")
                    return
                }
                CostumeAssignmentHandler.assign(target.uniqueId, slot, costumeId)
                CostumeAssignmentHandler.save()
                NetworkHandler.broadcastCostumeAssignment()
                sender.sendMessage("${ChatColor.GREEN}已为 ${target.name} 在 $slot 装备 $costumeId。")
            }
            "unequip" -> {
                val playerName = args.getOrNull(2) ?: run { costumeUsage(sender); return }
                val slot = args.getOrNull(3)
                val target = Bukkit.getPlayer(playerName) ?: run {
                    sender.sendMessage("${ChatColor.RED}玩家 '$playerName' 不存在。")
                    return
                }
                if (slot != null && slot !in validSlots) {
                    sender.sendMessage("${ChatColor.RED}无效槽位 '$slot'。可用: ${validSlots.joinToString()}")
                    return
                }
                CostumeAssignmentHandler.remove(target.uniqueId, slot)
                CostumeAssignmentHandler.save()
                NetworkHandler.broadcastCostumeAssignment()
                sender.sendMessage("${ChatColor.GREEN}已为 ${target.name} 卸下${if (slot != null) " $slot" else " 全部槽位"}。")
            }
            else -> costumeUsage(sender)
        }
    }

    private fun costumeUsage(sender: CommandSender) {
        helpHeader(sender, "装扮")
        helpLine(sender, "lantern costume equip", "<玩家> <槽位> <装扮ID>", "为玩家装备装扮（槽位: full_body/back/tail/head/effect）")
        helpLine(sender, "lantern costume unequip", "<玩家> [槽位]", "卸下装扮（不带槽位 = 全部）")
    }

    private fun handleAnim(sender: CommandSender, args: Array<out String?>) {
        val sub = args.getOrNull(1)?.lowercase()
        val animation = args.getOrNull(2)
        if (sub !in setOf("play", "stop", "pause", "resume", "seek") || animation == null) {
            animUsage(sender)
            return
        }
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}控制台无法定位附近实体；请改用 NetworkHandler API。")
            return
        }
        val target = findAnimTarget(player) ?: run {
            sender.sendMessage("${ChatColor.RED}8 格内没有带自定义名字的实体。")
            return
        }
        val displayName = target.customName?.let { " (${ChatColor.stripColor(it)})" } ?: ""
        when (sub) {
            "play" -> {
                val transition = (args.getOrNull(3)?.toIntOrNull() ?: 5).coerceAtLeast(0)
                val loop = !args.getOrNull(4).equals("once", ignoreCase = true)
                val speed = args.getOrNull(5)?.toFloatOrNull()?.takeIf { it > 0.01f } ?: 1.0f
                NetworkHandler.playAnimation(target, animation, transition, loop, speed)
                sender.sendMessage("${ChatColor.GREEN}正在 ${target.type}$displayName 上播放 '$animation'（${if (loop) "loop" else "once"} x$speed）。")
            }
            "stop" -> {
                val transition = (args.getOrNull(3)?.toIntOrNull() ?: 0).coerceAtLeast(0)
                NetworkHandler.stopAnimation(target, animation, transition)
                sender.sendMessage("${ChatColor.GREEN}已停止 ${target.type}$displayName 上的 '$animation'。")
            }
            "pause" -> {
                NetworkHandler.pauseAnimation(target, animation)
                sender.sendMessage("${ChatColor.GREEN}已暂停 ${target.type}$displayName 上的 '$animation'。")
            }
            "resume" -> {
                NetworkHandler.resumeAnimation(target, animation)
                sender.sendMessage("${ChatColor.GREEN}已恢复 ${target.type}$displayName 上的 '$animation'。")
            }
            "seek" -> {
                val seconds = args.getOrNull(3)?.toFloatOrNull()?.takeIf { it >= 0f } ?: run {
                    sender.sendMessage("${ChatColor.RED}用法: /lantern anim seek <动画名> <秒数>")
                    return
                }
                NetworkHandler.seekAnimation(target, animation, seconds)
                sender.sendMessage("${ChatColor.GREEN}已将 ${target.type}$displayName 上的 '$animation' 跳转到 ${seconds}s。")
            }
        }
    }

    /** /lantern var <key> <value|del>：设置/删除 8 格内最近已命名实体的 molang 变量 */
    private fun handleVar(sender: CommandSender, args: Array<out String?>) {
        val key = args.getOrNull(1)
        val value = args.getOrNull(2)
        if (key == null || value == null) {
            sender.sendMessage("${ChatColor.RED}用法: /lantern var <键名> <值|del>")
            sender.sendMessage("${ChatColor.RED}       值支持数字或 molang 表达式（如 math.sin(query.time_stamp/100)）")
            return
        }
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}控制台无法定位附近实体；请改用 NetworkHandler API。")
            return
        }
        val target = findAnimTarget(player) ?: run {
            sender.sendMessage("${ChatColor.RED}8 格内没有带自定义名字的实体。")
            return
        }
        if (value.equals("del", ignoreCase = true)) {
            NetworkHandler.setMolangVariables(target, mapOf(key to ""))
            sender.sendMessage("${ChatColor.GREEN}已删除附近实体的 molang 变量 '$key'。")
        } else {
            NetworkHandler.setMolangVariables(target, mapOf(key to value))
            sender.sendMessage("${ChatColor.GREEN}已设置附近实体 '$key=$value'。")
        }
    }

    /** 最近的带自定义名字的生物（Lantern 实体模型按自定义名匹配），8 格范围内。 */
    private fun findAnimTarget(player: Player): LivingEntity? =
        player.getNearbyEntities(8.0, 8.0, 8.0)
            .filterIsInstance<LivingEntity>()
            .filter { it.customName != null }
            .minByOrNull { it.location.distanceSquared(player.location) }

    private fun animUsage(sender: CommandSender) {
        helpHeader(sender, "动画控制")
        helpLine(sender, "lantern anim play", "<动画名> [过渡tick] [loop|once] [速度]", "对 8 格内最近的命名实体播放动画")
        helpLine(sender, "lantern anim stop", "<动画名> [过渡tick]", "停止指定动画")
        helpLine(sender, "lantern anim pause", "<动画名>", "暂停播控动画（冻结时间轴）")
        helpLine(sender, "lantern anim resume", "<动画名>", "恢复暂停的动画")
        helpLine(sender, "lantern anim seek", "<动画名> <秒数>", "跳转动画时间轴（loop 取模 / once 钳制）")
        helpLine(sender, "lantern var", "<键名> <值|del>", "设置/删除附近实体的 molang 变量")
    }

    /** /lantern cam：越肩相机管理（camera.yml 配置，方向键微调之外的管理入口）。 */
    private fun handleCam(sender: CommandSender, args: Array<out String?>) {
        val service = org.lantern.camera.ShoulderCameraService
        when (args.getOrNull(1)?.lowercase() ?: "info") {
            "info" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                val state = service.stateOf(target)
                sender.sendMessage(
                    "${ChatColor.GOLD}${target.name}: 越肩 ${if (state.enabled) "开" else "关"}, " +
                        "offset-x=${state.offsetX}, offset-y=${state.offsetY}, distance=${state.distance} " +
                        "(服务端${if (service.globalEnabled()) "已启用" else "已关闭"})"
                )
            }
            "toggle" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                if (service.toggle(target) == null) {
                    sender.sendMessage("${ChatColor.RED}越肩相机已被服务端全服关闭（camera.yml shoulder.enabled）。")
                } else {
                    val state = service.stateOf(target)
                    sender.sendMessage("${ChatColor.GREEN}已${if (state.enabled) "开启" else "关闭"} ${target.name} 的越肩相机。")
                }
            }
            "reset" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                service.reset(target)
                val state = service.stateOf(target)
                sender.sendMessage(
                    "${ChatColor.GREEN}已重置 ${target.name} 的越肩相机 " +
                        "(offset-x=${state.offsetX}, offset-y=${state.offsetY}, distance=${state.distance})。"
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
                sender.sendMessage("${ChatColor.GREEN}已设置 ${target.name} 的 $param=$value（限幅后 $applied）。")
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
                sender.sendMessage("${ChatColor.GREEN}正在锁定 ${target.name} 的视角到 ($x, $y, $z)。")
            }
            "lockentity" -> {
                val tail = parseCamTail(args, 2)
                val target = camTarget(sender, tail.playerName) ?: return
                val origin = sender as? Player ?: target
                val entity = findAnimTarget(origin) ?: run {
                    sender.sendMessage("${ChatColor.RED}${origin.name} 8 格内没有带自定义名字的实体。")
                    return
                }
                NetworkHandler.cameraLockEntity(
                    target, entity.uniqueId,
                    tail.numbers.getOrElse(0) { 0.3 },
                    tail.numbers.getOrElse(1) { 0.0 },
                    tail.sync
                )
                sender.sendMessage("${ChatColor.GREEN}正在将 ${target.name} 的视角锁定到 ${entity.type}。")
            }
            "unlock" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                NetworkHandler.cameraUnlock(target)
                sender.sendMessage("${ChatColor.GREEN}已解除 ${target.name} 的视角锁定。")
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
                sender.sendMessage("${ChatColor.GREEN}正在震动 ${target.name} 的相机。")
            }
            "fov" -> {
                val tail = parseCamTail(args, 2)
                val target = camTarget(sender, tail.playerName) ?: return
                NetworkHandler.cameraFov(target, tail.numbers.getOrNull(0), tail.numbers.getOrElse(1) { 1.0 })
                sender.sendMessage("${ChatColor.GREEN}${target.name} 的相机 FOV: ${tail.numbers.getOrNull(0) ?: "恢复" }。")
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
                sender.sendMessage("${ChatColor.GREEN}已为 ${target.name} 设置相机偏移: pitch=$pitch yaw=$yaw。")
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
                    sender.sendMessage("${ChatColor.GREEN}正在为 ${target.name} 播放运镜路径 '$id'（speed=$speed）。")
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
                sender.sendMessage("${ChatColor.GREEN}已让 ${target.name} 的相机前往观察点 ($x, $y, $z) 看向 ($lx, $ly, $lz)。")
            }
            "clear" -> {
                val target = camTarget(sender, args.getOrNull(2)) ?: return
                NetworkHandler.cameraClear(target)
                sender.sendMessage("${ChatColor.GREEN}已清除 ${target.name} 的相机演出状态。")
            }
            else -> camUsage(sender)
        }
    }

    /** /lantern campath：客户端打点编辑运镜路径（会话在服务端内存，save 落盘）。 */
    private fun handleCampath(sender: CommandSender, args: Array<out String?>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}运镜路径打点必须由游戏内玩家执行（播放可改用 /lantern cam path <路径ID> [玩家]）。")
            return
        }
        val service = org.lantern.camera.CameraPathService
        val message: String = when (args.getOrNull(1)?.lowercase()) {
            "start" -> {
                val id = args.getOrNull(2) ?: run {
                    sender.sendMessage("${ChatColor.RED}用法: /lantern campath start <路径ID>")
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
        helpHeader(sender, "运镜打点")
        helpLine(sender, "lantern campath start", "<路径ID>", "开始/继续打点（已存档自动载入）")
        helpLine(sender, "lantern campath add", "[t-tick] [linear|smooth|hold] [fov]", "在当前机位打关键帧（数字按序=t/fov）")
        helpLine(sender, "lantern campath undo", "", "撤销上一个关键帧")
        helpLine(sender, "lantern campath clear", "", "清空会话内全部关键帧")
        helpLine(sender, "lantern campath list", "", "列出当前关键帧")
        helpLine(sender, "lantern campath preview", "", "粒子预览（火苗=关键帧，青线=段）")
        helpLine(sender, "lantern campath play", "[速度]", "会话内预演（不必先保存）")
        helpLine(sender, "lantern campath save", "", "保存到 cameraPaths/<路径ID>.yml")
        helpLine(sender, "lantern campath stop", "", "结束编辑会话")
    }

    /** cam 子命令的目标玩家：指定名取在线玩家，否则执行者，控制台必须指定。 */
    private fun camTarget(sender: CommandSender, name: String?): Player? {
        if (name != null) {
            val target = Bukkit.getPlayer(name)
            if (target == null) sender.sendMessage("${ChatColor.RED}玩家 '$name' 不存在。")
            return target
        }
        val self = sender as? Player
        if (self == null) sender.sendMessage("${ChatColor.RED}控制台必须指定玩家名。")
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
        helpHeader(sender, "相机")
        helpLine(sender, "lantern cam info", "[玩家]", "查看越肩相机状态")
        helpLine(sender, "lantern cam toggle", "[玩家]", "开关越肩相机（第四视角）")
        helpLine(sender, "lantern cam set", "<offset-x|offset-y|distance> <值> [玩家]", "设置越肩参数（受限幅约束）")
        helpLine(sender, "lantern cam reset", "[玩家]", "恢复越肩默认参数")
        helpLine(sender, "lantern cam lock", "<x> <y> <z> [平滑秒] [持续秒] [sync] [玩家]", "锁定视角看向坐标")
        helpLine(sender, "lantern cam lockentity", "[平滑秒] [持续秒] [sync] [玩家]", "视角跟随附近命名实体")
        helpLine(sender, "lantern cam unlock", "[玩家]", "解除视角锁定")
        helpLine(sender, "lantern cam shake", "[幅度] [频率] [时长秒] [玩家]", "相机震动（默认线性衰减）")
        helpLine(sender, "lantern cam fov", "[度数] [过渡秒] [玩家]", "临时 FOV（不带度数 = 恢复）")
        helpLine(sender, "lantern cam offset", "<pitch> <yaw> [roll] [过渡秒] [玩家]", "朝向偏移叠加")
        helpLine(sender, "lantern cam path", "<路径ID> [玩家] [速度]", "播放打点运镜")
        helpLine(sender, "lantern cam watch", "<x> <y> <z> <看向x> <看向y> <看向z> [持续秒] [平滑秒] [玩家]", "固定点观察")
        helpLine(sender, "lantern cam clear", "[玩家]", "清除全部演出状态（不影响越肩）")
    }

    // ============ 命令帮助渲染 ============
    // 标题: 深青加粗插件名 + 版本 + 板块名；条目: ▪ 命令(深青) 参数(灰) - 说明(白)；
    // 玩家端条目可点击（建议填入命令）、悬停显示提示；控制台退化为纯文本。

    private fun helpHeader(sender: CommandSender, section: String) {
        val version = org.lantern.LanternPlugin.instance.description.version
        sender.sendMessage(
            "${ChatColor.DARK_AQUA}${ChatColor.BOLD}Lantern ${ChatColor.GRAY}v$version " +
                "${ChatColor.DARK_GRAY}${ChatColor.STRIKETHROUGH}» ${ChatColor.WHITE}$section"
        )
    }

    private fun helpLine(sender: CommandSender, path: String, argsSpec: String, desc: String) {
        val text = "${ChatColor.DARK_GRAY}▪ ${ChatColor.DARK_AQUA}/$path" +
            (if (argsSpec.isEmpty()) "" else " ${ChatColor.GRAY}$argsSpec") +
            " ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}$desc"
        if (sender !is Player) {
            sender.sendMessage(text)
            return
        }
        val components = TextComponent.fromLegacyText(text)
        val suggest = "/$path "
        components.forEach { component ->
            component.clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggest)
            component.hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                arrayOf(TextComponent("${ChatColor.GRAY}点击填入 ${ChatColor.WHITE}$suggest"))
            )
        }
        sender.spigot().sendMessage(*components)
    }

    /** /lantern help：全命令总览（按板块分组）。 */
    private fun showHelp(sender: CommandSender) {
        helpHeader(sender, "命令帮助")
        sender.sendMessage("${ChatColor.DARK_AQUA}基础")
        helpLine(sender, "lantern reload", "", "重载全部配置并推送在线玩家")
        helpLine(sender, "lantern open", "<界面ID> [玩家]", "打开 UI 界面")
        helpLine(sender, "lantern give", "<方块ID> [玩家] [数量]", "给予自定义方块")
        helpLine(sender, "lantern wardrobe", "[玩家]", "打开衣橱")
        sender.sendMessage("${ChatColor.DARK_AQUA}动画")
        helpLine(sender, "lantern anim play", "<动画名> [过渡tick] [loop|once] [速度]", "播放动画")
        helpLine(sender, "lantern anim stop", "<动画名> [过渡tick]", "停止动画")
        helpLine(sender, "lantern var", "<键名> <值|del>", "设置/删除 molang 变量")
        sender.sendMessage("${ChatColor.DARK_AQUA}相机")
        helpLine(sender, "lantern cam", "", "越肩与演出指令（回车查看全部子命令）")
        helpLine(sender, "lantern campath", "", "运镜路径打点（回车查看全部子命令）")
        sender.sendMessage("${ChatColor.DARK_AQUA}装扮")
        helpLine(sender, "lantern costume equip", "<玩家> <槽位> <装扮ID>", "装备装扮")
        helpLine(sender, "lantern costume unequip", "<玩家> [槽位]", "卸下装扮")
    }

    private fun handleWardrobe(sender: CommandSender, args: Array<out String?>) {
        val target: Player = args.getOrNull(1)
            ?.let { name ->
                Bukkit.getPlayer(name) ?: run {
                    sender.sendMessage("${ChatColor.RED}玩家 '$name' 不存在。")
                    return
                }
            }
            ?: (sender as? Player) ?: run {
                sender.sendMessage("${ChatColor.RED}控制台必须指定玩家名。")
                return
            }
        WardrobeGui.openMain(target)
        sender.sendMessage("${ChatColor.GREEN}已为 ${target.name} 打开衣橱。")
    }
}
