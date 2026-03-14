package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.canvas.impl.GuiCanvas
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.widget.IWidget

object OverlayRenderer {

    fun tryRenderOverlay(screen: Screen, graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (screen is GuiCanvas) return
        val overlays = UiScreenStorage.getOverlaysForTitle(screen.title.string)
        if (overlays.isEmpty()) return

        val w = screen.width
        val h = screen.height
        for (root in overlays) {
            LayoutCache.getOrCompute(root, w, h)
            WidgetRendererRegistry.render(root, graphics, mouseX, mouseY, delta)
            EventDispatcher.updateHover(root, mouseX, mouseY)
            TooltipRenderer.renderTooltipPass(root, graphics, mouseX, mouseY)
        }
    }

    fun handleClick(screen: Screen, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (screen is GuiCanvas) return false
        val overlays = UiScreenStorage.getOverlaysForTitle(screen.title.string)
        if (overlays.isEmpty()) return false

        for (root in overlays) {
            EventDispatcher.dispatchClick(root, mouseX.toInt(), mouseY.toInt(), button)
        }
        return false
    }
}
