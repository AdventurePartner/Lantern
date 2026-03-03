package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.text.TextWidgetImpl

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
        val x = style.getInt(StyleProperty.X)
        val y = style.getInt(StyleProperty.Y)
        val colorHex = style.getString(StyleProperty.COLOR, "#ffffff")
        val color = parseColor(colorHex)
        val font = Minecraft.getInstance().font
        graphics.drawString(font, widget.text, x, y, color, false)
    }

    private fun parseColor(hex: String): Int {
        return try {
            val clean = hex.trimStart('#')
            if (clean.length == 6) (0xFF shl 24) or clean.toInt(16) else clean.toInt(16)
        } catch (_: NumberFormatException) {
            0xFFFFFFFF.toInt()
        }
    }
}
