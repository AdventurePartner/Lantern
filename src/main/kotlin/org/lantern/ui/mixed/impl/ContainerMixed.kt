package org.lantern.ui.mixed.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import org.lantern.ui.layer.ILayer
import org.lantern.ui.layer.gui.GuiLayerImpl
import org.lantern.ui.mixed.IMixed
import org.lantern.ui.misc.impl.GuiMisc

class ContainerMixed(
    val layer: GuiLayerImpl,
    menu: ChestMenu,
    inventory: Inventory,
    title: Component
) : IMixed, ContainerScreen(menu, inventory, title) {
    private val params = layer.getMisc() as GuiMisc

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        if (!params.override) {
            super.renderBg(graphics, partialTick, screenWidth, screenHeight)
        }
        this.layer.onComponentsRender(graphics, partialTick, screenWidth, screenHeight)
    }

    override fun getLayer(): ILayer {
        return layer
    }
}
