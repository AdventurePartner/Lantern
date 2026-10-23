package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.lantern.uix.IComponent
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.widget.IWidget

object TooltipRenderer {
    fun renderTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!EventDispatcher.hasHoveredComponents()) return
        val tooltip = findDeepestTooltip(root) ?: return
        graphics.setTooltipForNextFrame(
            Minecraft.getInstance().font,
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
