package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.stat.ProgressBarWidgetImpl

object ProgressBarRenderer : IWidgetRenderer<ProgressBarWidgetImpl> {

    override fun render(
        widget: ProgressBarWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val stat = widget.stat ?: return
        val player = Minecraft.getInstance().player ?: return
        if (style.getBoolean(StyleProperty.AUTO_HIDE) && !stat.visible(player)) return

        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        val w = WidgetGeometry.width(widget, ProgressBarWidgetImpl.DEFAULT_WIDTH)
        val h = WidgetGeometry.height(widget, ProgressBarWidgetImpl.DEFAULT_HEIGHT)
        BackgroundPainter.paint(graphics, style, x, y, w, h)
        if (w <= 0 || h <= 0) return

        val state = stat.state(player)
        texture(widget.textures, state, "background")?.let { ImageRenderer.drawTexture(graphics, it, x, y, w, h) }
        val fill = texture(widget.textures, state, "fill") ?: return
        val max = stat.max(player)
        val fraction = if (max > 0f) (stat.value(player) / max).coerceIn(0f, 1f) else 0f
        when (style.getString(StyleProperty.FILL_DIRECTION, "right").lowercase()) {
            "left" -> {
                val filled = Math.round(w * fraction)
                ImageRenderer.drawTextureCropped(graphics, fill, x, y, w, h, w - filled, 0, filled, h)
            }
            "up" -> {
                val filled = Math.round(h * fraction)
                ImageRenderer.drawTextureCropped(graphics, fill, x, y, w, h, 0, h - filled, w, filled)
            }
            "down" -> {
                val filled = Math.round(h * fraction)
                ImageRenderer.drawTextureCropped(graphics, fill, x, y, w, h, 0, 0, w, filled)
            }
            else -> {
                val filled = Math.round(w * fraction)
                ImageRenderer.drawTextureCropped(graphics, fill, x, y, w, h, 0, 0, filled, h)
            }
        }
    }

    /** 先找 `<状态>-<部位>`，没有就用 `<部位>`。 */
    private fun texture(textures: Map<String, String>, state: String?, part: String): String? =
        state?.let { textures["$it-$part"] } ?: textures[part]
}
