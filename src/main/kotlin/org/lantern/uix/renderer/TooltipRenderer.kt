package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.lantern.uix.IComponent
import org.lantern.uix.widget.IWidget

object TooltipRenderer {

    fun renderTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltip = findDeepestTooltip(root) ?: return
        val font = Minecraft.getInstance().font
        graphics.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY)
    }

    private fun findDeepestTooltip(component: IComponent): String? {
        // Depth-first: check children first, deepest hovered widget wins
        for (child in component.getChildren()) {
            val childTooltip = findDeepestTooltip(child)
            if (childTooltip != null) return childTooltip
        }
        if (component is IWidget && component.hovered && component.tooltip.isNotEmpty()) {
            return component.tooltip
        }
        return null
    }
}
