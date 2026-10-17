package org.lantern.platform

import net.minecraft.world.entity.player.Player

object InventoryBridge {
    @JvmStatic
    fun selectedSlot(player: Player): Int = player.inventory.selected
}
