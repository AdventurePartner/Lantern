package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.Background
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.button.ButtonWidgetImpl
import org.lantern.uix.util.ColorUtil

object ButtonRenderer : IWidgetRenderer<ButtonWidgetImpl> {

    private val DEFAULT_BACKGROUND = Background.Color(0xFF333333.toInt())

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

        BackgroundPainter.paint(graphics, style, x, y, w, h, DEFAULT_BACKGROUND)

        val colorHex = style.getString(StyleProperty.COLOR, "#ffffff")
        val color = ColorUtil.parseColor(colorHex, 0xFFFFFFFF.toInt())
        val font = Minecraft.getInstance().font
        val textX = x + (w - font.width(widget.text)) / 2
        val textY = y + (h - font.lineHeight) / 2
        graphics.drawString(font, widget.text, textX, textY, color, false)
    }
}
