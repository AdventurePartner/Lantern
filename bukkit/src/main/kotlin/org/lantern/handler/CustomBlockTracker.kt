package org.lantern.handler

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.lantern.LanternPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 自定义方块位置追踪器。
 * 维护 worldName → (packedPos → variation) 映射，支持持久化。
 * 使用脏标记 + 定时异步保存，避免每次方块变更同步写文件阻塞主线程。
 */
object CustomBlockTracker {

    /** world name → (packed_pos → variation) */
    private val blocks = ConcurrentHashMap<String, ConcurrentHashMap<Long, Int>>()

    @Volatile
    private var dirty = false

    /** 定时保存任务 ID，用于关闭时取消 */
    private var saveTaskId = -1

    /**
     * 将方块坐标打包为 long。
     * x: 26 bits, z: 26 bits, y: 12 bits
     */
    fun packPos(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

    /** 26 位有符号解包：shl 6 将 bit25 移到 bit31（符号位），再 shr 6 算术右移做符号扩展 */
    fun unpackX(packed: Long): Int = (((packed shr 38) and 0x3FFFFFF).toInt() shl 6) shr 6
    fun unpackZ(packed: Long): Int = (((packed shr 12) and 0x3FFFFFF).toInt() shl 6) shr 6
    fun unpackY(packed: Long): Int = (packed and 0xFFF).toInt()

    fun add(world: String, x: Int, y: Int, z: Int, variation: Int) {
        blocks.getOrPut(world) { ConcurrentHashMap() }[packPos(x, y, z)] = variation
    }

    fun remove(world: String, x: Int, y: Int, z: Int): Boolean {
        return blocks[world]?.remove(packPos(x, y, z)) != null
    }

    fun getVariation(world: String, x: Int, y: Int, z: Int): Int? {
        return blocks[world]?.get(packPos(x, y, z))
    }

    fun getAll(world: String): Map<Long, Int> = blocks[world] ?: emptyMap()

    fun getAllWorlds(): Map<String, Map<Long, Int>> = blocks

    fun clear() {
        blocks.clear()
    }

    /**
     * 标记数据已变更，待下次定时任务异步保存。
     * 替代每次方块变更后的同步 save()。
     */
    fun markDirty() {
        dirty = true
    }

    /**
     * 启动定时异步保存任务（每 60 秒检查一次脏标记）。
     * 应在 onEnable 中调用。
     */
    fun startAutoSave(plugin: LanternPlugin) {
        // 60秒 = 1200 ticks
        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (dirty) {
                dirty = false
                saveToFile()
            }
        }, 1200L, 1200L).taskId
    }

    /**
     * 停止定时保存任务。应在 onDisable 中调用。
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
        return File(dataDir, "blocks.yml")
    }

    /**
     * 同步保存。仅在 onDisable 中直接调用，确保关服时数据不丢失。
     * 平时由 startAutoSave() 的定时任务异步调用 saveToFile()。
     */
    fun save() {
        dirty = false
        saveToFile()
    }

    /**
     * 实际写入文件。对 blocks 做快照遍历，对 ConcurrentHashMap 安全。
     */
    private fun saveToFile() {
        val file = getDataFile()
        val config = YamlConfiguration()
        blocks.forEach { (world, posMap) ->
            val list = posMap.map { (packed, variation) ->
                val x = unpackX(packed)
                val y = unpackY(packed)
                val z = unpackZ(packed)
                mapOf("x" to x, "y" to y, "z" to z, "v" to variation)
            }
            config.set(world, list)
        }
        config.save(file)
    }

    fun load() {
        val file = getDataFile()
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        blocks.clear()
        for (world in config.getKeys(false)) {
            val list = config.getMapList(world)
            val posMap = ConcurrentHashMap<Long, Int>()
            for (entry in list) {
                val x = (entry["x"] as? Number)?.toInt() ?: continue
                val y = (entry["y"] as? Number)?.toInt() ?: continue
                val z = (entry["z"] as? Number)?.toInt() ?: continue
                val v = (entry["v"] as? Number)?.toInt() ?: continue
                posMap[packPos(x, y, z)] = v
            }
            blocks[world] = posMap
        }
        LanternPlugin.instance.logger.info("[Lantern] Loaded ${blocks.values.sumOf { it.size }} block positions")
    }
}