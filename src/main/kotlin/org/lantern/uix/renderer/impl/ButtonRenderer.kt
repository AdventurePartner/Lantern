package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.button.ButtonWidgetImpl
import org.lantern.uix.util.ColorUtil

object ButtonRenderer : IWidgetRenderer<ButtonWidgetImpl> {

    override fun render(
        widget: ButtonWidgetImpl,
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
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, 80)
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, 20)

        val bgHex = style.getString(StyleProperty.BACKGROUND, "#333333")
        val bg = ColorUtil.parseColor(bgHex, 0xCC333333.toInt())
        graphics.fill(x, y, x + w, y + h, bg)

        val colorHex = style.getString(StyleProperty.COLOR, "#ffffff")
        val color = ColorUtil.parseColor(colorHex, 0xFFFFFFFF.toInt())
        val font = Minecraft.getInstance().font
        val textX = x + (w - font.width(widget.text)) / 2
        val textY = y + (h - font.lineHeight) / 2
        graphics.drawString(font, widget.text, textX, textY, color, false)
    }
}
