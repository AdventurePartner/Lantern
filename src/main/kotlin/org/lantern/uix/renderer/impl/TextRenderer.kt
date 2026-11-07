package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.hud.HudStatTokens
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.text.TextWidgetImpl
import org.lantern.uix.util.ColorUtil
import org.lantern.uix.util.TextLines

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
        val font = Minecraft.getInstance().font
        val text = HudStatTokens.resolve(widget.text)
        val colorHex = style.getString(StyleProperty.COLOR, "#ffffff")
        val color = ColorUtil.parseColor(colorHex)
        val wrapWidth = TextLines.wrapWidth(style)

        if (TextLines.isSingleLine(font, text, wrapWidth)) {
            val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, font.width(text))
            val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, font.lineHeight)
            BackgroundPainter.paint(graphics, style, x, y, w, h)
            graphics.drawString(font, text, x, y, color, false)
            return
        }

        val lines = TextLines.layout(font, text, wrapWidth)
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, TextLines.width(font, lines))
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, lines.size * font.lineHeight)
        BackgroundPainter.paint(graphics, style, x, y, w, h)
        lines.forEachIndexed { index, line ->
            graphics.drawString(font, line, x, y + index * font.lineHeight, color, false)
        }
    }
}
