package org.lantern.listen

import org.bukkit.Material
import org.bukkit.block.data.type.Barrel as BarrelData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.lantern.handler.CacheHandler
import org.lantern.handler.CustomBlockTracker
import org.lantern.network.NetworkHandler

/**
 * 监听方块放置/破坏事件，管理自定义方块位置追踪。
 */
class BlockListener : Listener {

    private val carrierMaterials = setOf(Material.BARREL)

    /**
     * 方块放置：如果木桶物品携带 CustomModelData，
     * 注册位置映射。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type !in carrierMaterials) return

        val meta = item.itemMeta ?: return
        if (!meta.hasCustomModelData()) return
        val cmd = meta.customModelData

        val cache = CacheHandler.blockModelsByCmd[cmd] ?: return
        val variation = cache.customVariation

        val block = event.blockPlaced

        // 设置标记态：open=true，客户端通过此属性判断是否为自定义方块
        val data = block.blockData as? BarrelData
        if (data != null) {
            data.isOpen = true
            block.blockData = data
        }
        // 注册位置追踪
        val world = block.world.name
        CustomBlockTracker.add(world, block.x, block.y, block.z, variation)
        CustomBlockTracker.markDirty()

        // 通知同世界玩家
        NetworkHandler.sendBlockPositionUpdate(block.world, "add", block.x, block.y, block.z, variation)
    }

    /**
     * 方块破坏：如果是已追踪的自定义方块位置，清理追踪数据。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type !in carrierMaterials) return

        val world = block.world.name
        val x = block.x
        val y = block.y
        val z = block.z

        // 仅处理被追踪的自定义方块位置
        val variation = CustomBlockTracker.getVariation(world, x, y, z) ?: return
        if (!CustomBlockTracker.remove(world, x, y, z)) return

        // 取消原版木桶掉落物
        event.isDropItems = false

        // 自定义破坏音效
        val cache = CacheHandler.blockModelsByVariation[variation]
        if (cache != null && cache.breakSound.isNotBlank()) {
            block.world.playSound(
                block.location, cache.breakSound, 
                org.bukkit.SoundCategory.BLOCKS, 1.0f, 1.0f
            )
        }

        // 通知附近玩家移除此位置
        NetworkHandler.sendBlockPositionUpdate(block.world, "remove", x, y, z, 0)
        CustomBlockTracker.markDirty()
    }

    /**
     * 阻止打开自定义桶的容器 GUI。
     * 使用 InventoryOpenEvent 而非 PlayerInteractEvent，
     * 避免干扰方块放置的交互流程。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.inventory.type != InventoryType.BARREL) return
        val loc = event.inventory.location ?: return
        val world = loc.world?.name ?: return
        CustomBlockTracker.getVariation(world, loc.blockX, loc.blockY, loc.blockZ) ?: return
        event.isCancelled = true
    }
}