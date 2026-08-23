package org.lantern.handler

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.lantern.LanternPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家装扮分配（UUID → slot → costumeId）。
 * 纯内存结构 + 文件持久化（data/costumes.yml），脏标记 + 定时异步保存，onDisable 同步落盘。
 * 全量常驻内存，onQuit 不驱逐（与 CustomBlockTracker 一致）。
 */
object CostumeAssignmentHandler {
    // Player UUID -> (slot -> costumeId)
    private val assignments = ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>>()

    @Volatile
    private var dirty = false

    /** 定时保存任务 ID，用于关闭时取消 */
    private var saveTaskId = -1

    fun assign(playerUUID: UUID, slot: String, costumeId: String) {
        assignments.computeIfAbsent(playerUUID) { ConcurrentHashMap() }[slot] = costumeId
        markDirty()
    }

    fun remove(playerUUID: UUID, slot: String? = null) {
        if (slot == null) {
            assignments.remove(playerUUID)
        } else {
            assignments[playerUUID]?.remove(slot)
        }
        markDirty()
    }

    fun get(playerUUID: UUID): Map<String, String>? = assignments[playerUUID]?.toMap()

    fun getAll(): Map<UUID, Map<String, String>> = assignments.mapValues { it.value.toMap() }

    fun clear() {
        assignments.clear()
        markDirty()
    }

    /**
     * 标记数据已变更，待下次定时任务异步保存。
     */
    fun markDirty() {
        dirty = true
    }

    /**
     * 启动定时异步保存任务（每 60 秒检查一次脏标记）。
     */
    fun startAutoSave(plugin: LanternPlugin) {
        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (dirty) {
                dirty = false
                saveToFile()
            }
        }, 1200L, 1200L).taskId
    }

    /**
     * 停止定时保存任务。
     */
    fun stopAutoSave() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId)
            saveTaskId = -1
        }
    }

    // ==================== 持久化 ====================

    private fun getDataFile(): File {
        val dataDir = File(LanternPlugin.instance.dataFolder, "data")
        if (!dataDir.exists()) dataDir.mkdirs()
        return File(dataDir, "costumes.yml")
    }

    /**
     * 同步保存。关服时直接调用确保数据不丢失；命令路径也显式调用立即落盘。
     */
    fun save() {
        dirty = false
        saveToFile()
    }

    private fun saveToFile() {
        val file = getDataFile()
        val config = YamlConfiguration()
        assignments.forEach { (uuid, slotMap) ->
            val section = config.createSection(uuid.toString())
            slotMap.forEach { (slot, costumeId) -> section.set(slot, costumeId) }
        }
        config.save(file)
    }

    fun load() {
        val file = getDataFile()
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        assignments.clear()
        for (key in config.getKeys(false)) {
            val uuid = try {
                UUID.fromString(key)
            } catch (e: IllegalArgumentException) {
                continue
            }
            val section = config.getConfigurationSection(key) ?: continue
            val slotMap = ConcurrentHashMap<String, String>()
            for (slot in section.getKeys(false)) {
                section.getString(slot)?.let { slotMap[slot] = it }
            }
            if (slotMap.isNotEmpty()) assignments[uuid] = slotMap
        }
        LanternPlugin.instance.logger.info("[Lantern] Loaded ${assignments.size} costume assignments")
    }
}
