package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.image.ImageWidgetImpl

object ImageRenderer : IWidgetRenderer<ImageWidgetImpl> {

    override fun render(
        widget: ImageWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        if (widget.texture.isBlank()) return
        val x = style.getInt(StyleProperty.X)
        val y = style.getInt(StyleProperty.Y)
        val w = style.getInt(StyleProperty.WIDTH, 16)
        val h = style.getInt(StyleProperty.HEIGHT, 16)

        val loc = runCatching { ResourceLocation.parse(widget.texture) }.getOrNull() ?: return
        graphics.blit(loc, x, y, w, h, 0f, 0f, w, h, w, h)
    }
}
