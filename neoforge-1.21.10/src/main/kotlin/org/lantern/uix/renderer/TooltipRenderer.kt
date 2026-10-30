package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.lantern.uix.IComponent
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.interaction.InventorySlotInteraction
import org.lantern.uix.widget.IWidget

object TooltipRenderer {
    fun renderTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        val item = InventorySlotInteraction.hoveredItem(root)
        if (item != null) {
            graphics.setTooltipForNextFrame(font, item, mouseX, mouseY)
            return
        }
        if (!EventDispatcher.hasHoveredComponents()) return
        val tooltip = findDeepestTooltip(root) ?: return
        graphics.setTooltipForNextFrame(
            font,
            Component.literal(tooltip),
            mouseX,
            mouseY
        )
    }

    private fun findDeepestTooltip(component: IComponent): String? {
        component.getChildren().forEach { child ->
            findDeepestTooltip(child)?.let { return it }
        }
        return if (component is IWidget && component.hovered && component.tooltip.isNotEmpty()) {
            component.tooltip
        } else {
            null
        }
    }
}
