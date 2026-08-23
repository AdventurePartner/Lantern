package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.lantern.internal.handler.TextureHandler
import org.lantern.platform.IdentifierBridge
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.image.ImageWidgetImpl
import java.util.concurrent.ConcurrentHashMap

object ImageRenderer : IWidgetRenderer<ImageWidgetImpl> {
    private val locations = ConcurrentHashMap<String, ResourceLocation?>()

    override fun render(
        widget: ImageWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE) || widget.texture.isBlank()) return
        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        val width = rect?.width ?: style.getInt(StyleProperty.WIDTH, 16)
        val height = rect?.height ?: style.getInt(StyleProperty.HEIGHT, 16)
        val location = if (TextureHandler.isHttpUrl(widget.texture)) {
            TextureHandler.getTexture(widget.texture)
        } else {
            locations.getOrPut(widget.texture) {
                runCatching { IdentifierBridge.parse(widget.texture) }.getOrNull()
            } ?: return
        }
        graphics.blit(location, x, y, width, height, 0.0f, 1.0f, 0.0f, 1.0f)
    }
}
