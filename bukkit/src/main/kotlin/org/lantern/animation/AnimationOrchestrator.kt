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
 * 动画编排器（DragonAnimation 的 AnimationPipeline + Controller 模式）：
 *
 * - 动作轨道：animations.yml 里按「模型 key → 动画名」声明 tick 时刻的
 *   sound / command / mm-skill 动作，播放时由服务端调度执行；
 * - 生命周期事件：Start / Finish / Interrupt 三个 Bukkit 事件；
 * - 链式编排：轨道的 next 字段，播完自动接续；
 * - 打断语义：新播放覆盖旧动画时触发 InterruptEvent 并取消未执行的动作。
 */
object AnimationOrchestrator {

    data class SoundSpec(val sound: String, val volume: Float, val pitch: Float)

    data class TrackAction(
        val atTick: Long,
        val sound: SoundSpec?,
        val command: String?,
        val mmSkill: String?
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
                    actions.add(
                        TrackAction(
                            atTick = at,
                            sound = sound,
                            command = map["command"]?.toString()?.takeIf { it.isNotBlank() },
                            mmSkill = (map["mm-skill"] ?: map["mmSkill"])?.toString()?.takeIf { it.isNotBlank() }
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

    fun onClientFinished(uuidRaw: String, animation: String, event: String) {
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
    }

    private fun resolveModelKey(entity: Entity): String? {
        val name = entity.customName ?: return null
        val stripped = ChatColor.stripColor(name) ?: name.toString()
        return nameToModelKey[stripped]
    }

    fun reset() {
        active.values.forEach { a -> a.tasks.forEach { it.cancel() } }
        active.clear()
    }
}
