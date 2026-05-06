package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage

object HudRenderer {
    fun render(graphics: GuiGraphics, partialTick: Float) {
        if (!shouldRender()) return
        CanvasRenderer.hudCanvas.render(graphics, 0, 0, partialTick)
    }

    private fun shouldRender(): Boolean {
        val client = Minecraft.getInstance()
        if (client.options.hideGui) return false
        if (client.player == null || client.level == null) return false
        if (client.screen != null) return false
        if (UiScreenStorage.getAllOfType(ScreenType.HUD).isEmpty()) return false
        return true
    }
}
