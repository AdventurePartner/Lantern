package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.renderer.WidgetRendererRegistry
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
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
        val x = style.getInt(StyleProperty.X)
        val y = style.getInt(StyleProperty.Y)
        val w = style.getInt(StyleProperty.WIDTH)
        val h = style.getInt(StyleProperty.HEIGHT)

        val bgHex = style.getString(StyleProperty.BACKGROUND, "")
        if (bgHex.isNotBlank()) {
            val bg = parseColor(bgHex, 0x80000000.toInt())
            graphics.fill(x, y, x + w, y + h, bg)
        }

        // Translate the matrix so children use local (panel-relative) coordinates.
        // Mouse coords are inversely offset so hit-testing in child renderers stays correct.
        graphics.pose().pushPose()
        graphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
        widget.children.forEach { child ->
            WidgetRendererRegistry.render(child, graphics, mouseX - x, mouseY - y, delta)
        }
        graphics.pose().popPose()
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
