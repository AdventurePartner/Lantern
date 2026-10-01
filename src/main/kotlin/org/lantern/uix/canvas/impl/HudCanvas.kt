package org.lantern.uix.canvas.impl

import net.minecraft.client.gui.GuiGraphics
import org.lantern.internal.storage.HudLayer
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.canvas.BaseCanvas
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.UixRenderContext
import org.lantern.uix.renderer.WidgetRendererRegistry
import org.lantern.uix.widget.IWidget

class HudCanvas : BaseCanvas() {

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        renderLayer(arg, UiScreenStorage.hudRoots(HudLayer.TOP))
    }

    fun renderLayer(graphics: GuiGraphics, roots: List<IWidget>) {
        width = graphics.guiWidth()
        height = graphics.guiHeight()

        UixRenderContext.inHud {
            roots.forEach { rootWidget ->
                LayoutCache.getOrCompute(rootWidget, width, height)
                WidgetRendererRegistry.render(rootWidget, graphics, 0, 0, 0f)
            }
        }
    }
}
