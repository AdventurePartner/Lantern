package org.lantern.uix.renderer.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.lantern.internal.handler.TextureHandler
import org.lantern.platform.IdentifierBridge
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.BackgroundPainter
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
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        val width = rect?.width ?: style.getInt(StyleProperty.WIDTH, 16)
        val height = rect?.height ?: style.getInt(StyleProperty.HEIGHT, 16)
        BackgroundPainter.paint(graphics, style, x, y, width, height)
        drawTexture(graphics, widget.texture, x, y, width, height)
    }

    /** 整张贴图拉伸画到 (x, y, width, height)；texture 为资源路径或 http(s) URL。 */
    fun drawTexture(graphics: GuiGraphics, texture: String, x: Int, y: Int, width: Int, height: Int) {
        if (texture.isBlank()) return
        val location = if (TextureHandler.isHttpUrl(texture)) {
            TextureHandler.getTexture(texture)
        } else {
            locations.getOrPut(texture) {
                runCatching { IdentifierBridge.parse(texture) }.getOrNull()
            } ?: return
        }
        // 1.21.9+ 该重载的四个 int 是左上/右下角点 (x1, y1, x2, y2)，不是 x/y/width/height
        graphics.blit(location, x, y, x + width, y + height, 0.0f, 1.0f, 0.0f, 1.0f)
    }
}
