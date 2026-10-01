package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.internal.handler.TextureHandler
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.image.ImageWidgetImpl
import java.util.concurrent.ConcurrentHashMap

object ImageRenderer : IWidgetRenderer<ImageWidgetImpl> {
    private val rlCache = ConcurrentHashMap<String, ResourceLocation?>()

    override fun render(
        widget: ImageWidgetImpl,
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
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, 16)
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, 16)
        BackgroundPainter.paint(graphics, style, x, y, w, h)
        drawTexture(graphics, widget.texture, x, y, w, h)
    }

    /** 整张贴图拉伸画到 (x, y, w, h)；texture 为资源路径或 http(s) URL。 */
    fun drawTexture(graphics: GuiGraphics, texture: String, x: Int, y: Int, w: Int, h: Int) {
        if (texture.isBlank()) return
        val loc = if (TextureHandler.isHttpUrl(texture)) {
            TextureHandler.getTexture(texture)
        } else {
            rlCache.getOrPut(texture) {
                try { IdentifierBridge.parse(texture) } catch (_: Exception) { null }
            } ?: return
        }
        graphics.blit(loc, x, y, w, h, 0f, 0f, w, h, w, h)
    }
}
