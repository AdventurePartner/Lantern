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
 *         - {at: 5,  sound: {s: minecraft:entity.player.attack.sweep}}
 *         - {at: 8,  mm-skill: SwordDamage}
 *         - {at: 10, camera: {action: shake, amplitude: 0.3, radius: 16}}
 *       next: 右砍                # 播完自动接续（可选）
 *
 * at 是动画起播后的 tick 偏移。动作在服务端调度执行，因此伤害判定、命令、
 * 技能触发都发生在服务端——客户端只负责把动画演出来。
 */
object AnimationOrchestrator {

    data class SoundSpec(val sound: String, val volume: Float, val pitch: Float)

    /**
     * 相机演出节点：`{at: 40, camera: {action: shake, amplitude: 0.4, radius: 16}}`。
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
        val tasks: MutableList<BukkitTask>
    )

    private val active = ConcurrentHashMap<UUID, Active>()
    private var tracks: Map<String, Map<String, Track>> = emptyMap()
    private var nameToModelKey: Map<String, String> = emptyMap()

    fun load(plugin: LanternPlugin) {
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

    fun onPlay(entity: Entity, animation: String, loop: Boolean) {
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
            active[uuid] = Active(entity, animation, modelKey, mutableListOf())
            return
        }
        val plugin = LanternPlugin.instance
        val tasks = mutableListOf<BukkitTask>()
        for (action in track.actions) {
            tasks.add(
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { execute(entity, action) }, action.atTick)
            )
        }
        active[uuid] = Active(entity, animation, modelKey, tasks)
    }

    fun onStop(entity: Entity, animation: String) {
        val uuid = entity.uniqueId
        val current = active.remove(uuid) ?: return
        current.tasks.forEach { it.cancel() }
        Bukkit.getPluginManager().callEvent(LanternAnimationInterruptEvent(entity, animation))
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

    fun onClientFinished(uuidRaw: String, animation: String, event: String) {
        // 客户端本地动作（翻滚/连招）起播时上报，携带压制窗口：动作期间的原版
        // 近战输出交由动画轨道的伤害帧决定，不再「点一下立刻结算」
        if (event.startsWith("suppress:")) {
            val millis = event.removePrefix("suppress:").toLongOrNull() ?: return
            if (millis <= 0) return
            val uuid = runCatching { UUID.fromString(uuidRaw) }.getOrNull() ?: return
            attackSuppression[uuid] = System.currentTimeMillis() + millis
            return
        }
        // 无敌帧：翻滚闪避这类动作的免伤窗口，同样由客户端起播时上报
        if (event.startsWith("invuln:")) {
            val millis = event.removePrefix("invuln:").toLongOrNull() ?: return
            if (millis <= 0) return
            val uuid = runCatching { UUID.fromString(uuidRaw) }.getOrNull() ?: return
            invulnerability[uuid] = System.currentTimeMillis() + millis
            return
        }
        if (event != "finish") return
        val uuid = runCatching { UUID.fromString(uuidRaw) }.getOrNull() ?: return
        val finished = active.remove(uuid) ?: return
        if (finished.animation != animation) {
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
     *  兼容简写 `heal: anim` 与展开 `heal: {animation: anim, transition: N}` 两种 yml 写法 */
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
}
