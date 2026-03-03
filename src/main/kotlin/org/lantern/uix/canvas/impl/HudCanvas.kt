package org.lantern.uix.canvas.impl

import net.minecraft.client.gui.GuiGraphics
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.canvas.BaseCanvas
import org.lantern.uix.renderer.WidgetRendererRegistry

class HudCanvas : BaseCanvas() {

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        width = arg.guiWidth()
        height = arg.guiHeight()

        UiScreenStorage.getAllOfType(ScreenType.HUD).values.forEach { rootWidget ->
            WidgetRendererRegistry.render(rootWidget, arg, 0, 0, 0f)
        }
    }
}