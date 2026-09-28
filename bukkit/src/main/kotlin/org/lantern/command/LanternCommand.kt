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

    /** 子命令 -> 所需权限节点；不在表内 = 对所有人开放（help）。 */
    private val subPerms: Map<String, String> = mapOf(
        "reload" to "lantern.reload",
        "open" to "lantern.open",
        "give" to "lantern.give",
        "anim" to "lantern.anim",
        "var" to "lantern.anim",
        "skill" to "lantern.skill",
        "cam" to "lantern.camera",
        "campath" to "lantern.camera",
        "costume" to "lantern.costume",
        "wardrobe" to "lantern.wardrobe",
        "bind" to "lantern.bind",
        "unbind" to "lantern.bind",
        "binds" to "lantern.bind",
        "lock" to "lantern.lock",
        "unlock" to "lantern.lock",
        "image" to "lantern.image"
    )

    private fun denyIfMissing(sender: CommandSender, sub: String?): Boolean {
        val perm = sub?.let { subPerms[it] } ?: return false
        if (sender.hasPermission(perm)) return false
        sender.sendMessage("${ChatColor.RED}没有权限执行该命令，需要 $perm。")
        return true
    }

    /** help 条目可见性：路径第二段是子命令（如 "lantern cam lock" -> cam）。 */
    private fun canSee(sender: CommandSender, path: String): Boolean {
        val perm = path.split(' ').getOrNull(1)?.let { subPerms[it] } ?: return true
        return sender.hasPermission(perm)
    }


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String?>): Boolean {
        val sub = args.getOrNull(0)?.lowercase()
        if (denyIfMissing(sender, sub)) return true
        when (sub) {
            "reload" -> {
                Configurations.load()
                // 重载会让客户端全清播控（entityModels 包触发 RendererHandler.reload），
                // 服务端登记表跟着清，否则在播的实体全部卡在"演出中"
                org.lantern.animation.AnimationOrchestrator.reset()
                org.lantern.animation.AnimationOrchestrator.load(org.lantern.LanternPlugin.instance)
                org.lantern.animation.AnimationGroupService.load(org.lantern.LanternPlugin.instance)
                org.lantern.animation.AnimationGroupService.start(org.lantern.LanternPlugin.instance)
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
            "skill" -> handleSkill(sender, args)
            "var" -> handleVar(sender, args)
            "cam" -> handleCam(sender, args)
            "campath" -> handleCampath(sender, args)
            "costume" -> handleCostume(sender, args)
            "wardrobe" -> handleWardrobe(sender, args)
            "bind" -> handleBind(sender, args)
            "unbind" -> handleUnbind(sender, args)
            "binds" -> handleBindList(sender)
            "lock" -> handleLock(sender, args)
            "unlock" -> handleUnlock(sender, args)
            "image" -> handleImage(sender, args)
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
            // 一层补全按权限过滤：没权限的子命令不进入补全（二层参数补全由入口检查兜住）
            1 -> (subPerms.entries
                .filter { sender.hasPermission(it.value) }
                .map { it.key } + "help")
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
                "image" -> listOf("text", "texture", "spawn", "clear")
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "wardrobe", "lock", "unlock" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                // bind 的第一个参数是宿主 UUID：在线玩家的 UUID 是最常用的宿主
                "bind" -> Bukkit.getOnlinePlayers().map { it.uniqueId.toString() }
                    .filter { it.startsWith(args[1] ?: "", ignoreCase = true) }
                "unbind" -> org.lantern.bind.BindRegistry.all().map { it.follower.toString() }
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
                "lock" -> org.lantern.input.InputLockManager.ACTIONS
                    .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                "image" -> if (args[1]?.equals("spawn", ignoreCase = true) == true) {
                    CacheHandler.worldImages.images.keys
                        .filter { it.startsWith(args[2] ?: "", ignoreCase = true) }
                } else {
                    emptyList()
                }
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
        val player = playerSender(sender) ?: return
        val target = findAnimTarget(player) ?: run {
            noNamedTarget(sender)
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
        val player = playerSender(sender) ?: return
        val target = findAnimTarget(player) ?: run {
            noNamedTarget(sender)
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

    /**
     * 命令执行者必须是游戏内玩家；"附近"定位依赖执行者的位置。
     * 控制台/命令方块场景应改用 NetworkHandler 的 API（如 playAnimation）直接指定实体。
     */
    private fun playerSender(sender: CommandSender): Player? =
        sender as? Player ?: run {
            sender.sendMessage("${ChatColor.RED}该命令必须由游戏内玩家执行。")
            null
        }

    /** 找不到可操作目标的对外提示：告诉服主怎么补救，不暴露内部匹配机制。 */
    private fun noNamedTarget(sender: CommandSender, owner: String? = null) {
        val prefix = owner?.let { "$it " } ?: ""
        sender.sendMessage("${ChatColor.RED}${prefix}附近 8 格内没有已命名的生物，请先用命名牌命名。")
    }

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
                    noNamedTarget(sender, origin.name)
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

    /** /lantern help：全命令总览（按板块分组，条目按权限过滤，整组不可见则跳过标题）。 */
    private data class HelpEntry(val path: String, val argsSpec: String, val desc: String)

    private val helpGroups: List<Pair<String, List<HelpEntry>>> = listOf(
        "基础" to listOf(
            HelpEntry("lantern reload", "", "重载全部配置并推送在线玩家"),
            HelpEntry("lantern open", "<界面ID> [玩家]", "打开 UI 界面"),
            HelpEntry("lantern give", "<方块ID> [玩家] [数量]", "给予自定义方块"),
            HelpEntry("lantern wardrobe", "[玩家]", "打开衣橱")
        ),
        "动画" to listOf(
            HelpEntry("lantern anim play", "<动画名> [过渡tick] [loop|once] [速度]", "播放动画"),
            HelpEntry("lantern anim stop", "<动画名> [过渡tick]", "停止动画"),
            HelpEntry("lantern skill", "<玩家> <技能名>", "对玩家施放 MythicMobs 技能"),
            HelpEntry("lantern var", "<键名> <值|del>", "设置/删除 molang 变量")
        ),
        "相机" to listOf(
            HelpEntry("lantern cam", "", "越肩与演出指令（回车查看全部子命令）"),
            HelpEntry("lantern campath", "", "运镜路径打点（回车查看全部子命令）")
        ),
        "装扮" to listOf(
            HelpEntry("lantern costume equip", "<玩家> <槽位> <装扮ID>", "装备装扮"),
            HelpEntry("lantern costume unequip", "<玩家> [槽位]", "卸下装扮")
        ),
        "载体与输入" to listOf(
            HelpEntry("lantern bind", "<宿主UUID> <载体UUID> [x y z] [rotate] [visible] [毫秒]", "把载体渲染到宿主身上"),
            HelpEntry("lantern unbind", "<载体UUID>", "解除载体绑定"),
            HelpEntry("lantern binds", "", "列出当前全部绑定"),
            HelpEntry("lantern lock", "<玩家> <动作,动作> <毫秒>", "压住玩家输入（move/jump/sneak/turn/attack/use）"),
            HelpEntry("lantern unlock", "<玩家>", "解除玩家的全部输入锁")
        ),
        "世界图片" to listOf(
            HelpEntry("lantern image text", "<内容> [缩放]", "在自己头顶生成文字世界图片（3 秒）"),
            HelpEntry("lantern image texture", "<贴图路径> [边长]", "在自己头顶生成贴图世界图片（3 秒）"),
            HelpEntry("lantern image spawn", "<模板名>", "按 worldImages.yml 模板生成世界图片"),
            HelpEntry("lantern image clear", "", "清空自己客户端的全部世界图片")
        )
    )

    private fun showHelp(sender: CommandSender) {
        helpHeader(sender, "命令帮助")
        helpGroups.forEach { (title, entries) ->
            val visibleEntries = entries.filter { canSee(sender, it.path) }
            if (visibleEntries.isEmpty()) return@forEach
            sender.sendMessage("${ChatColor.DARK_AQUA}$title")
            visibleEntries.forEach { helpLine(sender, it.path, it.argsSpec, it.desc) }
        }
    }

    // ============ MM 技能施放 ============

    /**
     * /lantern skill <玩家> <技能名>
     *
     * 对玩家施放 MythicMobs 技能（caster = 该玩家）。这是按键放技能的服务端入口：
     * keys.yml 按键 -> console 命令 -> 本命令，对应灾厄外部按键插件的触发模式。
     * MM 5 免费版没有物品 Skills 手持触发（items 的 Skills 字段不被读取），
     * 技能触发不要往物品上挂
     */
    private fun handleSkill(sender: CommandSender, args: Array<out String?>) {
        val playerRaw = args.getOrNull(1)
        val skill = args.getOrNull(2)
        if (playerRaw == null || skill == null) {
            helpLine(sender, "lantern skill", "<玩家> <技能名>", "对玩家施放 MythicMobs 技能")
            return
        }
        val player = Bukkit.getPlayerExact(playerRaw) ?: run {
            sender.sendMessage("${ChatColor.RED}玩家不在线: $playerRaw")
            return
        }
        val result = runCatching {
            io.lumine.mythic.bukkit.MythicBukkit.inst().apiHelper.castSkill(player, skill)
        }
        val failure = result.exceptionOrNull()
        if (failure != null) {
            org.lantern.LanternPlugin.instance.logger.warning("skill '$skill' failed: ${failure.message}")
            sender.sendMessage("${ChatColor.RED}技能 '$skill' 施放异常（技能不存在或 MM 未加载）。")
        } else if (result.getOrNull() == true) {
            sender.sendMessage("${ChatColor.GREEN}已对 ${player.name} 施放技能 '$skill'。")
        } else {
            // castSkill 返回 false 是条件不通过（冷却中、aura 未消），不是配置错
            sender.sendMessage("${ChatColor.GRAY}技能 '$skill' 条件未满足，未施放。")
        }
    }

    // ============ 世界图片 ============

    /**
     * /lantern image：世界图片的调试与验收入口。生成类子命令只发给执行者本人，
     * 不影响其他在线玩家；模板与动画定义在 worldImages.yml
     */
    private fun handleImage(sender: CommandSender, args: Array<out String?>) {
        when (args.getOrNull(1)?.lowercase()) {
            "clear" -> {
                val player = playerSender(sender) ?: return
                org.lantern.worldimage.WorldImageService.clear(listOf(player))
                sender.sendMessage("${ChatColor.GREEN}已清空自己客户端的世界图片。")
            }
            "text" -> {
                val player = playerSender(sender) ?: return
                val text = args.getOrNull(2) ?: run { imageUsage(sender); return }
                val scale = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                val instance = com.google.gson.JsonObject()
                instance.addProperty("id", "cmd-${System.currentTimeMillis()}")
                instance.addProperty("type", "text")
                instance.addProperty("text", text)
                instance.addProperty("scale", scale)
                instance.addProperty("age", 60)
                spawnDebugImage(player, instance)
                sender.sendMessage("${ChatColor.GREEN}已生成文字世界图片（3 秒）。")
            }
            "texture" -> {
                val player = playerSender(sender) ?: return
                val path = args.getOrNull(2) ?: run { imageUsage(sender); return }
                val size = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                val instance = com.google.gson.JsonObject()
                instance.addProperty("id", "cmd-${System.currentTimeMillis()}")
                instance.addProperty("type", "texture")
                instance.addProperty("texture", path)
                instance.addProperty("size", size)
                instance.addProperty("age", 60)
                spawnDebugImage(player, instance)
                sender.sendMessage("${ChatColor.GREEN}已生成贴图世界图片（3 秒）。")
            }
            "spawn" -> {
                val player = playerSender(sender) ?: return
                val template = args.getOrNull(2) ?: run { imageUsage(sender); return }
                if (template !in CacheHandler.worldImages.images) {
                    sender.sendMessage("${ChatColor.RED}worldImages.yml 里没有模板 '$template'。")
                    return
                }
                val instance = com.google.gson.JsonObject()
                instance.addProperty("id", "cmd-${System.currentTimeMillis()}")
                instance.addProperty("template", template)
                instance.addProperty("age", 60)
                spawnDebugImage(player, instance)
                sender.sendMessage("${ChatColor.GREEN}已按模板 '$template' 生成世界图片（3 秒）。")
            }
            else -> imageUsage(sender)
        }
    }

    /** 调试实例统一绑定到执行者头顶一米处，坐标取实时 location。 */
    private fun spawnDebugImage(player: Player, instance: com.google.gson.JsonObject) {
        val bind = com.google.gson.JsonObject()
        bind.addProperty("world", player.world.key.toString())
        val pos = com.google.gson.JsonArray()
        pos.add(player.location.x)
        pos.add(player.location.y + 1.0)
        pos.add(player.location.z)
        bind.add("pos", pos)
        instance.add("bind", bind)
        org.lantern.worldimage.WorldImageService.spawn(listOf(player), listOf(instance))
    }

    private fun imageUsage(sender: CommandSender) {
        helpHeader(sender, "世界图片")
        helpLine(sender, "lantern image text", "<内容> [缩放]", "在自己头顶生成文字世界图片（3 秒）")
        helpLine(sender, "lantern image texture", "<贴图路径> [边长]", "在自己头顶生成贴图世界图片（3 秒）")
        helpLine(sender, "lantern image spawn", "<模板名>", "按 worldImages.yml 模板生成世界图片")
        helpLine(sender, "lantern image clear", "", "清空自己客户端的全部世界图片")
    }

    // ============ 载体绑定 ============

    /**
     * /lantern bind <宿主UUID> <载体UUID> [x y z] [rotate] [visible] [持续毫秒]
     *
     * 参数序是**宿主在前、载体在后**。MM 里 summon 载体后这样调：
     *   cmd{c="lantern bind <caster.uuid> <target.uuid> 0 1.5 0 true false";delay=1} @MIR{r=25;t=载体名;limit=1;sort=NEAREST}
     * delay 是必须的：summon 当帧实体还没落地到实体表，取不到 UUID。
     *
     * 不做成 MM mechanic：mechanic 的 targeter 只能表达一个目标，绑定要两个，
     * 命令形态已经覆盖全部用法
     */
    private fun handleBind(sender: CommandSender, args: Array<out String?>) {
        val hostRaw = args.getOrNull(1)
        val followerRaw = args.getOrNull(2)
        if (hostRaw == null || followerRaw == null) {
            bindUsage(sender)
            return
        }
        val hostUuid = parseUuid(hostRaw) ?: run {
            sender.sendMessage("${ChatColor.RED}宿主 UUID 格式不合法: $hostRaw")
            uuidHint(sender, hostRaw)
            return
        }
        val followerUuid = parseUuid(followerRaw) ?: run {
            sender.sendMessage("${ChatColor.RED}载体 UUID 格式不合法: $followerRaw")
            uuidHint(sender, followerRaw)
            return
        }
        val offsetX = args.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        val offsetY = args.getOrNull(4)?.toDoubleOrNull() ?: 0.0
        val offsetZ = args.getOrNull(5)?.toDoubleOrNull() ?: 0.0
        val rotate = args.getOrNull(6)?.toBoolean() ?: true
        val visible = args.getOrNull(7)?.toBoolean() ?: false
        val durationMs = args.getOrNull(8)?.toLongOrNull() ?: 0L

        val failure = org.lantern.bind.BindRegistry.bind(
            hostUuid, followerUuid, offsetX, offsetY, offsetZ, rotate, visible, durationMs
        )
        if (failure != null) {
            sender.sendMessage("${ChatColor.RED}绑定失败: $failure")
            return
        }
        sender.sendMessage(
            "${ChatColor.GREEN}已绑定载体 $followerUuid -> 宿主 $hostUuid" +
                "（偏移 $offsetX/$offsetY/$offsetZ，rotate=$rotate，visible=$visible，" +
                "${if (durationMs > 0) "${durationMs}ms" else "持续"}）。"
        )
    }

    private fun handleUnbind(sender: CommandSender, args: Array<out String?>) {
        val followerRaw = args.getOrNull(1) ?: run {
            bindUsage(sender)
            return
        }
        val followerUuid = parseUuid(followerRaw) ?: run {
            sender.sendMessage("${ChatColor.RED}载体 UUID 格式不合法: $followerRaw")
            return
        }
        if (org.lantern.bind.BindRegistry.unbind(followerUuid, "command")) {
            sender.sendMessage("${ChatColor.GREEN}已解绑载体 $followerUuid。")
        } else {
            sender.sendMessage("${ChatColor.RED}该载体没有绑定记录: $followerUuid")
        }
    }

    private fun handleBindList(sender: CommandSender) {
        val all = org.lantern.bind.BindRegistry.all()
        if (all.isEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}当前没有任何载体绑定。")
            return
        }
        helpHeader(sender, "载体绑定（${all.size}）")
        val now = System.currentTimeMillis()
        all.forEach { bind ->
            val remain = if (bind.expireAtMs > 0) "${bind.expireAtMs - now}ms" else "持续"
            sender.sendMessage(
                "${ChatColor.DARK_GRAY}▪ ${ChatColor.DARK_AQUA}${bind.follower}" +
                    " ${ChatColor.GRAY}-> ${ChatColor.WHITE}${bind.host}" +
                    " ${ChatColor.GRAY}偏移 ${bind.offsetX}/${bind.offsetY}/${bind.offsetZ}" +
                    " rotate=${bind.rotate} visible=${bind.visible} $remain"
            )
        }
    }

    private fun parseUuid(raw: String): java.util.UUID? =
        runCatching { java.util.UUID.fromString(raw) }.getOrNull()

    /**
     * UUID 解析失败时的追加提示。
     *
     * 从 MM 的 cmd 调过来时，形如 `<target.uuid>` 的字面量意味着那一行的 targeter
     * 没有命中实体——MM 会把整条命令按「无目标」再执行一次，占位符原样发过来。
     * 光报「格式不合法」会让人去查 UUID 本身，实际要查的是 targeter
     */
    private fun uuidHint(sender: CommandSender, raw: String) {
        if (!raw.startsWith("<") || !raw.endsWith(">")) return
        sender.sendMessage(
            "${ChatColor.GRAY}  这是没被替换的占位符，说明那一行的 targeter 没选中实体。" +
                "改用 lanternbind 机制（直接拿实体，不经字符串替换），" +
                "或用 summon 的 onsummonskill 回调把召唤出的载体设成目标。"
        )
    }

    private fun bindUsage(sender: CommandSender) {
        helpHeader(sender, "载体绑定")
        helpLine(
            sender, "lantern bind", "<宿主UUID> <载体UUID> [x y z] [rotate] [visible] [持续毫秒]",
            "把载体渲染到宿主身上（宿主在前，载体在后）"
        )
        helpLine(sender, "lantern unbind", "<载体UUID>", "解除该载体的绑定")
        helpLine(sender, "lantern binds", "", "列出当前全部绑定")
        sender.sendMessage("${ChatColor.GRAY}  rotate=true 时 x/y/z 是宿主朝向的局部坐标（x=右 y=上 z=前）")
        sender.sendMessage("${ChatColor.GRAY}  visible=true 时宿主脱离视野载体仍渲染；持续毫秒 0 = 直到实体消失")
    }

    // ============ 玩家输入锁 ============

    /** /lantern lock <玩家> <动作,动作> <毫秒> */
    private fun handleLock(sender: CommandSender, args: Array<out String?>) {
        val playerName = args.getOrNull(1)
        val actionsRaw = args.getOrNull(2)
        val millis = args.getOrNull(3)?.toLongOrNull()
        if (playerName == null || actionsRaw == null || millis == null) {
            lockUsage(sender)
            return
        }
        val target = Bukkit.getPlayer(playerName) ?: run {
            sender.sendMessage("${ChatColor.RED}玩家 '$playerName' 不存在。")
            return
        }
        val requested = actionsRaw.split(',')
        val applied = org.lantern.input.InputLockManager.lock(target, "command", requested, millis)
        if (applied.isEmpty()) {
            sender.sendMessage(
                "${ChatColor.RED}没有可用动作或时长非法。可用: ${org.lantern.input.InputLockManager.ACTIONS.joinToString()}"
            )
            return
        }
        sender.sendMessage("${ChatColor.GREEN}已锁定 ${target.name} 的 ${applied.joinToString()} ${millis}ms。")
    }

    private fun handleUnlock(sender: CommandSender, args: Array<out String?>) {
        val playerName = args.getOrNull(1) ?: run {
            lockUsage(sender)
            return
        }
        val target = Bukkit.getPlayer(playerName) ?: run {
            sender.sendMessage("${ChatColor.RED}玩家 '$playerName' 不存在。")
            return
        }
        org.lantern.input.InputLockManager.clear(target)
        sender.sendMessage("${ChatColor.GREEN}已解除 ${target.name} 的全部输入锁。")
    }

    private fun lockUsage(sender: CommandSender) {
        helpHeader(sender, "输入锁")
        helpLine(sender, "lantern lock", "<玩家> <动作,动作> <毫秒>", "压住玩家的指定输入")
        helpLine(sender, "lantern unlock", "<玩家>", "解除该玩家的全部输入锁")
        sender.sendMessage(
            "${ChatColor.GRAY}  动作: ${org.lantern.input.InputLockManager.ACTIONS.joinToString()}"
        )
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
