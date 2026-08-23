package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.renderer.WidgetRendererRegistry
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.util.ColorUtil
import org.lantern.uix.widget.panel.PanelWidgetImpl

object PanelRenderer : IWidgetRenderer<PanelWidgetImpl> {
    override fun render(
        widget: PanelWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        val width = rect?.width ?: style.getInt(StyleProperty.WIDTH)
        val height = rect?.height ?: style.getInt(StyleProperty.HEIGHT)
        val background = style.getString(StyleProperty.BACKGROUND, "")
        if (background.isNotBlank()) {
            graphics.fill(x, y, x + width, y + height, ColorUtil.parseColor(background, 0x80000000.toInt()))
        }

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        widget.children.forEach { child ->
            WidgetRendererRegistry.render(child, graphics, mouseX - x, mouseY - y, delta)
        }
        graphics.pose().popMatrix()
    }
}
