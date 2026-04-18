package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.text.TextWidgetImpl
import org.lantern.uix.util.ColorUtil

object TextRenderer : IWidgetRenderer<TextWidgetImpl> {

    override fun render(
        widget: TextWidgetImpl,
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
        val colorHex = style.getString(StyleProperty.COLOR, "#ffffff")
        val color = ColorUtil.parseColor(colorHex)
        val font = Minecraft.getInstance().font
        graphics.drawString(font, widget.text, x, y, color, false)
    }
}
