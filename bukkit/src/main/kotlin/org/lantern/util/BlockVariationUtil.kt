package org.lantern.util

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.Barrel as BarrelData

/**
 * 自定义方块工具类。
 * 使用木桶作为自定义方块载体，open=true 作为标记态。
 */
object BlockVariationUtil {

    /**
     * 创建木桶标记态 BlockData（open=true）。
     */
    fun createCarrierBlockData(): BlockData {
        val data = Bukkit.createBlockData(Material.BARREL) as BarrelData
        data.isOpen = true
        return data
    }
}
