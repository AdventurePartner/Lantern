package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.vanilla.VanillaElementWidgetImpl

/** 原版元素由原版自己画（被平移到节点处），这里只画节点的 background 占位。 */
object VanillaElementRenderer : IWidgetRenderer<VanillaElementWidgetImpl> {

    override fun render(
        widget: VanillaElementWidgetImpl,
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
        val w = WidgetGeometry.width(widget, widget.element?.naturalWidth ?: 0)
        val h = WidgetGeometry.height(widget, widget.element?.naturalHeight ?: 0)
        BackgroundPainter.paint(graphics, style, x, y, w, h)
    }
}
