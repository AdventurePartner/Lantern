package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.input.InputWidgetImpl

object InputRenderer : IWidgetRenderer<InputWidgetImpl> {

    private const val PADDING = 4

    override fun render(
        widget: InputWidgetImpl,
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
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, 120)
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, 20)

        // Background
        val bgHex = style.getString(StyleProperty.BACKGROUND, "#1a1a1a")
        graphics.fill(x, y, x + w, y + h, parseColor(bgHex, 0xFF1a1a1a.toInt()))

        // Border — brighter blue when focused
        val borderDefault = if (widget.focused) "#5588ff" else "#555555"
        val borderHex = style.getString(StyleProperty.BORDER_COLOR, borderDefault)
        val borderColor = parseColor(borderHex, if (widget.focused) 0xFF5588ff.toInt() else 0xFF555555.toInt())
        graphics.fill(x,         y,         x + w,     y + 1,     borderColor)
        graphics.fill(x,         y + h - 1, x + w,     y + h,     borderColor)
        graphics.fill(x,         y,         x + 1,     y + h,     borderColor)
        graphics.fill(x + w - 1, y,         x + w,     y + h,     borderColor)

        val font = Minecraft.getInstance().font
        val textY = y + (h - font.lineHeight) / 2
        val maxTextWidth = w - PADDING * 2

        if (widget.value.isEmpty() && !widget.focused) {
            val phHex = style.getString(StyleProperty.PLACEHOLDER_COLOR, "#888888")
            val phColor = parseColor(phHex, 0xFF888888.toInt())
            val ph = font.plainSubstrByWidth(widget.placeholder, maxTextWidth)
            graphics.drawString(font, ph, x + PADDING, textY, phColor, false)
        } else {
            syncViewStart(widget, font, maxTextWidth)

            val displayText = font.plainSubstrByWidth(widget.value.substring(widget.viewStart), maxTextWidth)
            val colorHex = style.getString(StyleProperty.COLOR, "#ffffff")
            val color = parseColor(colorHex, 0xFFFFFFFF.toInt())
            graphics.drawString(font, displayText, x + PADDING, textY, color, false)

            // Blinking cursor when focused
            if (widget.focused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                val visibleCursor = (widget.cursorPos - widget.viewStart).coerceIn(0, displayText.length)
                val cursorX = x + PADDING + font.width(displayText.substring(0, visibleCursor))
                graphics.fill(cursorX, textY - 1, cursorX + 1, textY + font.lineHeight + 1, color)
            }
        }
    }

    /**
     * Adjusts [widget.viewStart] so the cursor always stays within the visible text area.
     */
    private fun syncViewStart(widget: InputWidgetImpl, font: Font, maxWidth: Int) {
        if (widget.cursorPos < widget.viewStart) {
            widget.viewStart = widget.cursorPos
        }
        while (widget.viewStart < widget.cursorPos) {
            val visible = font.plainSubstrByWidth(widget.value.substring(widget.viewStart), maxWidth)
            if (widget.cursorPos <= widget.viewStart + visible.length) break
            widget.viewStart++
        }
    }

    private fun parseColor(hex: String, fallback: Int): Int {
        return try {
            val clean = hex.trimStart('#')
            if (clean.length == 6) (0xFF shl 24) or clean.toInt(16) else clean.toInt(16)
        } catch (_: NumberFormatException) {
            fallback
        }
    }
}
