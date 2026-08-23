package org.lantern.listen

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.type.Barrel as BarrelData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.lantern.LanternPlugin
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
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type !in carrierMaterials) return

        val blockWorld = block.world
        val world = blockWorld.name
        val x = block.x
        val y = block.y
        val z = block.z

        // 仅处理被追踪的自定义方块位置
        if (!CustomBlockTracker.remove(world, x, y, z)) return

        // 取消原版木桶掉落物
        event.isDropItems = false

        CustomBlockTracker.markDirty()

        // 等原版破坏事件发出后再移除客户端映射，确保粒子和声音仍能按位置取到配置。
        Bukkit.getScheduler().runTaskLater(
            LanternPlugin.instance,
            Runnable {
                if (CustomBlockTracker.getVariation(world, x, y, z) == null) {
                    NetworkHandler.sendBlockPositionUpdate(blockWorld, "remove", x, y, z, 0)
                }
            },
            1L
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        scheduleExplosionCleanup(event.blockList())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        scheduleExplosionCleanup(event.blockList())
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

    private fun scheduleExplosionCleanup(blocks: List<Block>) {
        val tracked = blocks.mapNotNull { block ->
            if (block.type !in carrierMaterials) return@mapNotNull null
            val variation = CustomBlockTracker.getVariation(
                block.world.name,
                block.x,
                block.y,
                block.z
            ) ?: return@mapNotNull null
            TrackedBlock(block.world.name, block.x, block.y, block.z, variation)
        }
        if (tracked.isEmpty()) return

        Bukkit.getScheduler().runTaskLater(
            LanternPlugin.instance,
            Runnable {
                var changed = false
                tracked.forEach { block ->
                    val world = Bukkit.getWorld(block.world) ?: return@forEach
                    if (world.getBlockAt(block.x, block.y, block.z).type in carrierMaterials) {
                        return@forEach
                    }
                    if (CustomBlockTracker.getVariation(block.world, block.x, block.y, block.z) != block.variation) {
                        return@forEach
                    }
                    if (CustomBlockTracker.remove(block.world, block.x, block.y, block.z)) {
                        changed = true
                        NetworkHandler.sendBlockPositionUpdate(world, "remove", block.x, block.y, block.z, 0)
                    }
                }
                if (changed) CustomBlockTracker.markDirty()
            },
            1L
        )
    }

    private data class TrackedBlock(
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val variation: Int
    )
}
