package org.lantern.handler

import org.lantern.cache.BlockModelCache
import org.lantern.cache.CostumeCache
import org.lantern.cache.ItemIconCache
import org.lantern.cache.KeyCache
import java.util.concurrent.ConcurrentHashMap

object CacheHandler {
    val keys: MutableMap<String, KeyCache> = ConcurrentHashMap()
    val itemIcons: MutableMap<Int, ItemIconCache> = ConcurrentHashMap()
    val costumes: MutableMap<String, CostumeCache> = ConcurrentHashMap()
    val blockModels: MutableMap<String, BlockModelCache> = ConcurrentHashMap()

    /**
     * P1 玩家宿主化：进服自动套上的整替外观 id（costumes.yml 顶层 player-default.costume）。
     * 空 = 不自动分配；玩家已有 full_body 分配时不覆盖（手动/衣橱优先）
     */
    @Volatile
    var defaultPlayerCostume: String? = null

    // Reverse indices for O(1) lookup by customModelData and customVariation
    val blockModelsByCmd: MutableMap<Int, BlockModelCache> = ConcurrentHashMap()
    val blockModelsByVariation: MutableMap<Int, BlockModelCache> = ConcurrentHashMap()

    fun rebuildBlockModelIndices() {
        blockModelsByCmd.clear()
        blockModelsByVariation.clear()
        for (cache in blockModels.values) {
            if (cache.customModelData > 0) {
                blockModelsByCmd[cache.customModelData] = cache
            }
            blockModelsByVariation[cache.customVariation] = cache
        }
    }
}