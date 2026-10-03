package org.lantern.animation

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask
import org.lantern.LanternPlugin
import org.lantern.config.Configurations
import org.lantern.network.NetworkHandler

/**
 * 动画编排器（时间线流水线 + 控制器模式）：
 *
 * - 动作轨道：animations.yml 里按「模型 key → 动画名」声明 tick 时刻的
 *   sound / command / mm-skill 动作，播放时由服务端调度执行；
 * - 生命周期事件：Start / Finish / Interrupt 三个 Bukkit 事件；
 * - 链式编排：轨道的 next 字段，播完自动接续；
 * - 打断语义：新播放覆盖旧动画时触发 InterruptEvent 并取消未执行的动作。
 *
 * animations.yml 的「模型 key」两种来源：
 *   实体 —— entityModels.yml 的条目名（按实体自定义名反查）；
 *   玩家 —— costumes.yml 的外观条目名（按玩家已分配的 full_body 外观取，
 *           回落 player-default.costume）。
 *
 * 所以给玩家技能挂伤害帧、特效帧就是这样写：
 *
 *   player_default:              # 外观条目名
 *     左砍:                       # 该外观动画库里的动画名
 *       actions:
 *         - at: 5
 *           sound:
 *             s: minecraft:entity.player.attack.sweep
 *         - at: 8
 *           mm-skill: SwordDamage
 *         - at: 10
 *           camera:
 *             action: shake
 *             amplitude: 0.3
 *             radius: 16
 *       next: 右砍                # 播完自动接续（可选）
 *
 * at 是动画起播后的 tick 偏移。动作在服务端调度执行，因此伤害判定、命令、
 * 技能触发都发生在服务端——客户端只负责把动画演出来。
 */
object AnimationOrchestrator {

    data class SoundSpec(val sound: String, val volume: Float, val pitch: Float)

    /**
     * 相机演出节点：track action 下配 `at` 与 `camera`（camera 内含 action/amplitude/radius 等字段）。
     * 除 radius/action 外的字段直通 packet 18（如 smooth/duration/sync/value/transition/
     * pitch/yaw/roll/x/y/z/entity）；action=path 时用 id 引用 cameraPaths 存档。
     * 对被编排实体 radius 半径内的同世界玩家广播。
     */
    data class CameraSpec(val radius: Double, val params: Map<String, Any?>)

    data class TrackAction(
        val atTick: Long,
        val sound: SoundSpec?,
        val command: String?,
        val mmSkill: String?,
        val camera: CameraSpec?
    )

    data class Track(val animation: String, val next: String?, val actions: List<TrackAction>)

    private data class Active(
        val entity: Entity,
        val animation: String,
        val modelKey: String,
        val tasks: MutableList<BukkitTask>,
        /** loop 播控没有自然结束，不该被当成"演出进行中"挡住换组 */
        val loop: Boolean,
        /** 下发时的实例序号；finish 回带它按实例匹配，连点同技能时上一实例的 finish 才不会撞掉新实例 */
        val seq: Long
    )

    private val active = ConcurrentHashMap<UUID, Active>()
    private var sweeper: BukkitTask? = null
    private var tracks: Map<String, Map<String, Track>> = emptyMap()
    private var nameToModelKey: Map<String, String> = emptyMap()

    fun load(plugin: LanternPlugin) {
        startSweeper(plugin)
        val file = File(plugin.dataFolder, "animations.yml")
        if (!file.exists()) {
            runCatching { plugin.saveResource("animations.yml", false) }
        }
        val config = YamlConfiguration.loadConfiguration(file)
        val loaded = LinkedHashMap<String, MutableMap<String, Track>>()
        for (modelKey in config.getKeys(false)) {
            val modelSection = config.getConfigurationSection(modelKey) ?: continue
            val byName = LinkedHashMap<String, Track>()
            for (animName in modelSection.getKeys(false)) {
                val animSection = modelSection.getConfigurationSection(animName) ?: continue
                val actions = mutableListOf<TrackAction>()
                animSection.getMapList("actions").forEach { raw ->
                    @Suppress("UNCHECKED_CAST")
                    val map = raw as? Map<String, Any> ?: return@forEach
                    val at = (map["at"] as? Number)?.toLong() ?: return@forEach
                    val sound = (map["sound"] as? Map<*, *>)?.let { s ->
                        SoundSpec(
                            sound = s["s"]?.toString() ?: return@let null,
                            volume = (s["v"] as? Number)?.toFloat() ?: 1.0f,
                            pitch = (s["p"] as? Number)?.toFloat() ?: 1.0f
                        )
                    }
                    val camera = (map["camera"] as? Map<*, *>)?.let { c ->
                        val action = c["action"]?.toString() ?: return@let null
                        val params = LinkedHashMap<String, Any?>()
                        c.forEach { (key, value) ->
                            if (key != "action" && key != "radius" && value != null) {
                                params[key.toString()] = value
                            }
                        }
                        params["action"] = action
                        CameraSpec(
                            radius = (c["radius"] as? Number)?.toDouble() ?: 16.0,
                            params = params
                        )
                    }
                    actions.add(
                        TrackAction(
                            atTick = at,
                            sound = sound,
                            command = map["command"]?.toString()?.takeIf { it.isNotBlank() },
                            mmSkill = (map["mm-skill"] ?: map["mmSkill"])?.toString()?.takeIf { it.isNotBlank() },
                            camera = camera
                        )
                    )
                }
                actions.sortBy { it.atTick }
                byName[animName] = Track(
                    animation = animName,
                    next = animSection.getString("next")?.takeIf { it.isNotBlank() },
                    actions = actions
                )
            }
            loaded[modelKey] = byName
        }
        tracks = loaded
        rebuildNameIndex()
    }

    /** 实体自定义名（去色）→ entityModels.yml key 的索引，重载配置后重建 */
    private fun rebuildNameIndex() {
        val index = HashMap<String, String>()
        val models = Configurations.models
        for (key in models.getKeys(false)) {
            val name = models.getString("$key.name") ?: continue
            val stripped = ChatColor.stripColor(name) ?: name
            index[stripped] = key
        }
        nameToModelKey = index
    }

    fun onPlay(entity: Entity, animation: String, loop: Boolean, seq: Long = -1L) {
        val uuid = entity.uniqueId
        active.remove(uuid)?.let { previous ->
            previous.tasks.forEach { it.cancel() }
            Bukkit.getPluginManager().callEvent(
                LanternAnimationInterruptEvent(previous.entity, previous.animation)
            )
        }
        Bukkit.getPluginManager().callEvent(LanternAnimationStartEvent(entity, animation))

        val modelKey = resolveModelKey(entity) ?: return
        val track = tracks[modelKey]?.get(animation) ?: run {
            active[uuid] = Active(entity, animation, modelKey, mutableListOf(), loop, seq)
            return
        }
        val plugin = LanternPlugin.instance
        val tasks = mutableListOf<BukkitTask>()
        for (action in track.actions) {
            tasks.add(
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { execute(entity, action) }, action.atTick)
            )
        }
        active[uuid] = Active(entity, animation, modelKey, tasks, loop, seq)
    }

    /**
     * 每秒一轮回收失效条目：实体死亡/移除后客户端只是静默清表，不会上报 finish，
     * 非玩家的 loop 播控（特效载体的环绕动画）每放一次就在这里漏一条 Entity 强引用
     */
    private fun startSweeper(plugin: LanternPlugin) {
        if (sweeper != null) return
        sweeper = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val iterator = active.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!entry.value.entity.isValid) {
                    entry.value.tasks.forEach { it.cancel() }
                    iterator.remove()
                }
            }
        }, 20L, 20L)
    }

    fun onStop(entity: Entity, animation: String) {
        val uuid = entity.uniqueId
        val current = active.remove(uuid) ?: return
        current.tasks.forEach { it.cancel() }
        Bukkit.getPluginManager().callEvent(LanternAnimationInterruptEvent(entity, animation))
    }

    /**
     * 该实体此刻是否有服务端播控动画在放。
     *
     * 用于「演出期间不许换动画库」：播控指令是相对某一套外观的动画库下发的，
     * 演出中途换外观等于把动画库抽走，客户端查不到剪辑，正在播的技能动作
     * 当场断掉——表现为玩家模型突然从隐藏状态弹回来
     */
    fun isAnimating(uuid: UUID): Boolean = active[uuid]?.loop == false

    /** 退服清理：客户端不会再上报 finish，条目留着会永久挡住该玩家的换组 */
    fun forget(uuid: UUID) {
        active.remove(uuid)?.tasks?.forEach { it.cancel() }
    }

    /** 客户端上报的「原版攻击压制」窗口到期时刻（玩家 UUID -> 毫秒时间戳） */
    private val attackSuppression = ConcurrentHashMap<UUID, Long>()

    /** 该玩家此刻是否处于压制窗口内（伤害监听器查询） */
    fun isAttackSuppressed(uuid: UUID): Boolean = isWithin(attackSuppression, uuid)

    /** 动作无敌帧窗口（玩家 UUID -> 到期毫秒时间戳） */
    private val invulnerability = ConcurrentHashMap<UUID, Long>()

    /** 该玩家此刻是否处于无敌帧内 */
    fun isInvulnerable(uuid: UUID): Boolean = isWithin(invulnerability, uuid)

    private fun isWithin(table: ConcurrentHashMap<UUID, Long>, uuid: UUID): Boolean {
        val until = table[uuid] ?: return false
        if (System.currentTimeMillis() >= until) {
            table.remove(uuid)
            return false
        }
        return true
    }

    fun clearSuppression(uuid: UUID) {
        attackSuppression.remove(uuid)
        invulnerability.remove(uuid)
    }

    /**
     * 客户端 C2S 动画事件。
     *
     * 信任边界：包里的 UUID 与时长都是客户端写的。suppress/invuln/request 只允许作用于
     * 发包者本人，时长一律以服务端配置为准（客户端只是告诉服务端"我起播了哪条动作"）；
     * finish/missing 允许指向别的实体——观察者客户端替实体上报是设计如此——但发包者
     * 必须确实在该实体的广播范围内
     */
    fun onClientFinished(sender: org.bukkit.entity.Player, uuidRaw: String, animation: String, event: String) {
        val selfUuid = sender.uniqueId
        // 客户端本地动作（翻滚/连招）起播时上报，携带压制窗口：动作期间的原版
        // 近战输出交由动画轨道的伤害帧决定，不再「点一下立刻结算」。
        // animation 字段填的是动作 id，时长从服务端自己的定义表取
        if (event.startsWith("suppress:")) {
            val millis = org.lantern.handler.CacheHandler.playerActions[animation]?.suppressAttackMs ?: return
            if (millis <= 0) return
            attackSuppression[selfUuid] = System.currentTimeMillis() + millis
            return
        }
        // 无敌帧：翻滚闪避这类动作的免伤窗口，同样由客户端起播时上报
        if (event.startsWith("invuln:")) {
            val millis = org.lantern.handler.CacheHandler.playerActions[animation]?.invulnerableMs ?: return
            if (millis <= 0) return
            invulnerability[selfUuid] = System.currentTimeMillis() + millis
            return
        }
        // server-checked 动作的出招请求：客户端不本地播放，改上报由服务端把关
        if (event.startsWith("request:")) {
            handleActionRequest(selfUuid, animation, event.removePrefix("request:"))
            return
        }
        val uuid = runCatching { UUID.fromString(uuidRaw) }.getOrNull() ?: return
        if (uuid != selfUuid) {
            val target = Bukkit.getEntity(uuid) ?: return
            if (!NetworkHandler.isNearby(target, sender)) return
        }
        // 客户端在当前库里找不到剪辑，已放弃播放：清条目但不算完成，不触发 Finish 事件与链式接续
        if (event == "missing") {
            val current = active[uuid] ?: return
            if (current.animation == animation && active.remove(uuid, current)) {
                current.tasks.forEach { it.cancel() }
                LanternPlugin.instance.logger.warning(
                    "[Lantern] 客户端报告动画 '$animation' 在目标 $uuid 的动画库里不存在，演出未播出（技能动画请用 file= 指定库）"
                )
            }
            return
        }
        if (event != "finish" && !event.startsWith("finish:")) return
        val finished = active.remove(uuid) ?: return
        val reportedSeq = event.removePrefix("finish:").toLongOrNull()
        // 按实例匹配：连点同一技能时，上一实例的 finish 在 RTT 内到达会撞掉新实例，
        // 新实例的伤害帧被取消。老客户端不带序号时才回落按名匹配
        val stale = if (reportedSeq != null && finished.seq >= 0L) {
            reportedSeq != finished.seq
        } else {
            finished.animation != animation
        }
        if (stale) {
            // 过期事件（已被新动画覆盖），放回当前状态
            active.putIfAbsent(uuid, finished)
            return
        }
        finished.tasks.forEach { it.cancel() }
        Bukkit.getPluginManager().callEvent(
            LanternAnimationFinishEvent(finished.entity, finished.animation)
        )
        // 链式编排
        val track = tracks[finished.modelKey]?.get(finished.animation)
        val next = track?.next
        if (next != null && finished.entity.isValid) {
            NetworkHandler.playAnimation(finished.entity, next)
        }
    }

    /**
     * server-checked 动作的请求处理。
     *
     * 抛 [org.lantern.action.LanternPlayerActionRequestEvent]，附属在这里扣能量、查冷却；
     * 没有监听者时事件不会被取消，动作照常播出——配了 server-checked 但没装附属
     * 也能用，只是多一个 RTT。
     *
     * 只接受配置里确实标了 server-checked 的条目：其余条目本来就走本地触发，
     * 收到这类上报说明来源不对，直接丢弃
     */
    private fun handleActionRequest(uuid: UUID, actionId: String, direction: String) {
        val player = Bukkit.getPlayer(uuid) ?: return
        val action = org.lantern.handler.CacheHandler.playerActions[actionId] ?: return
        if (!org.lantern.action.PlayerActionGateway.isServerChecked(actionId, action.serverChecked)) return
        val animation = resolveDirectionalAnimation(action, direction) ?: return

        val requestEvent = org.lantern.action.LanternPlayerActionRequestEvent(player, actionId, direction)
        Bukkit.getPluginManager().callEvent(requestEvent)
        if (requestEvent.isCancelled) return

        val speed = action.speed.toFloat().let { if (kotlin.math.abs(it) > 0.01f) it else 1.0f }
        // 剪辑从动作自己的动画库取（与本地触发同源），不依赖玩家当前外观的库。
        // 不传 uninterruptible：播控通道的霸体是给 Boss 技能挡后续播控用的，
        // 一条翻滚要是带着它进来，0.8s 内到达的技能播控会被客户端整个拒绝而服务端照常结算伤害
        NetworkHandler.playAnimation(
            player,
            animation,
            action.transition,
            loop = false,
            speed = speed,
            layer = action.layer,
            file = action.file,
            exitTicks = action.exitTransition
        )
    }

    /**
     * 方向 -> 动画名。与客户端同一套回落链：配了斜向用斜向，没配回落到相邻主方向，
     * 再回落 none。两侧必须一致，否则服务端播的段和玩家按键期望的段对不上
     */
    private fun resolveDirectionalAnimation(
        action: org.lantern.cache.PlayerActionCache,
        direction: String
    ): String? {
        val dirs = action.directions
        if (dirs.isEmpty()) return null
        val fallback = when (direction) {
            "forward_left", "forward_right" -> "forward"
            "backward_left", "backward_right" -> "backward"
            else -> "none"
        }
        return dirs[direction] ?: dirs[fallback] ?: dirs["none"] ?: dirs.values.firstOrNull()
    }

    private fun execute(entity: Entity, action: TrackAction) {
        if (!entity.isValid) return
        action.sound?.let { spec ->
            entity.world.playSound(entity.location, spec.sound, spec.volume, spec.pitch)
        }
        action.command?.let { cmd ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        }
        action.mmSkill?.let { skill ->
            runCatching {
                io.lumine.mythic.bukkit.MythicBukkit.inst().apiHelper.castSkill(entity, skill)
            }.onFailure {
                LanternPlugin.instance.logger.warning("mm-skill '$skill' failed: ${it.message}")
            }
        }
        action.camera?.let { spec -> executeCamera(entity, spec) }
    }

    /** 相机演出节点：path 走存档载入的专用广播，其余参数直通 packet 18 广播。 */
    private fun executeCamera(entity: Entity, spec: CameraSpec) {
        when (spec.params["action"]) {
            "path" -> {
                val id = spec.params["id"]?.toString() ?: return
                val loaded = org.lantern.camera.CameraPathService.framesOf(id) ?: run {
                    LanternPlugin.instance.logger.warning("camera track path '$id' missing or has fewer than 2 keyframes")
                    return
                }
                val speed = (spec.params["speed"] as? Number)?.toDouble() ?: 1.0
                NetworkHandler.cameraPathBroadcast(entity.location, spec.radius, loaded.frames, speed, loaded.loop)
            }
            else -> NetworkHandler.cameraBroadcast(entity.location, spec.radius, spec.params)
        }
    }

    private fun resolveModelKey(entity: Entity): String? {
        // 玩家不在 entityModels.yml 里（那张表按实体自定义名索引），编排轨道要对玩家
        // 生效就得按外观 id 索引：animations.yml 用 costume 条目名（如 player_default）
        // 作模型 key，与 costumes.yml 对齐。没有这一条，玩家技能动画的时间线打点
        // （伤害帧/特效帧/音效/镜头）全部不执行——动画只是好看，打不出东西
        if (entity is org.bukkit.entity.Player) {
            return playerModelKey(entity)
        }
        val name = entity.customName ?: return null
        val stripped = ChatColor.stripColor(name) ?: name.toString()
        return nameToModelKey[stripped]
    }

    /** 玩家的编排索引键：已分配的 full_body 外观，回落服务端默认外观 */
    private fun playerModelKey(player: org.bukkit.entity.Player): String? {
        org.lantern.handler.CostumeAssignmentHandler.get(player.uniqueId)?.let { slots ->
            slots["full_body"]?.let { return it }
            slots.values.firstOrNull()?.let { return it }
        }
        return org.lantern.handler.CacheHandler.defaultPlayerCostume
    }

    /** 状态的动画名 + 切换过渡 tick（供事件驱动播放，取 yml 自定义或默认表） */
    data class StateEntry(val animation: String, val transition: Int)

    /**
     * 状态默认切换过渡 tick：一次性动作起手要快（2-3 tick），持续姿态 5 tick。
     * NetworkHandler 下发展开简写状态时用同一张表（单一事实源，勿两处分叉）
     */
    fun defaultTransitionOf(state: String): Int = when (state) {
        "jump", "landing" -> 2
        "falling", "spawn", "pull_bow", "heal", "attack" -> 3
        else -> 5
    }

    /** 实体对应模型某状态的播放条目（无模型或未配置该状态返回 null），供事件驱动播放。
     *  兼容简写 `heal: anim` 与展开（heal 下配 animation 与 transition）两种 yml 写法 */
    fun stateEntryOf(entity: Entity, state: String): StateEntry? {
        val key = resolveModelKey(entity) ?: return null
        val path = "$key.animations.states.$state"
        val section = Configurations.models.getConfigurationSection(path)
        if (section != null) {
            val animation = section.getString("animation")?.takeIf { it.isNotBlank() } ?: return null
            return StateEntry(animation, section.getInt("transition", defaultTransitionOf(state)))
        }
        val simple = Configurations.models.getString(path)?.takeIf { it.isNotBlank() } ?: return null
        return StateEntry(simple, defaultTransitionOf(state))
    }

    /** 实体对应模型映射的 death 状态动画名（无模型或无 death 映射返回 null），供死亡拦截使用 */
    fun deathAnimationOf(entity: Entity): String? = stateEntryOf(entity, "death")?.animation

    fun reset() {
        active.values.forEach { a -> a.tasks.forEach { it.cancel() } }
        active.clear()
    }

    fun shutdown() {
        reset()
        sweeper?.cancel()
        sweeper = null
    }
}
