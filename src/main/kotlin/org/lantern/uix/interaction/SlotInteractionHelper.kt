package org.lantern.uix.interaction

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ClickType

object SlotInteractionHelper {

    fun handleSlotClick(slotIndex: Int, button: Int) {
        val mc = Minecraft.getInstance()
        val screen = mc.screen
        if (screen !is AbstractContainerScreen<*>) return
        val player = mc.player ?: return
        val containerId = screen.menu.containerId
        mc.gameMode?.handleInventoryMouseClick(containerId, slotIndex, button, ClickType.PICKUP, player)
    }
}
