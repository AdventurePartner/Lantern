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
    private val locations = ConcurrentHashMap<String, ResourceLocation>()
    // ConcurrentHashMap 不能存 null，非法路径单独记下，避免每帧重复解析
    private val invalidPaths = ConcurrentHashMap.newKeySet<String>()

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
        val location = resolve(texture) ?: return
        // 1.21.9+ 该重载的四个 int 是左上/右下角点 (x1, y1, x2, y2)，不是 x/y/width/height
        graphics.blit(location, x, y, x + width, y + height, 0.0f, 1.0f, 0.0f, 1.0f)
    }

    /**
     * 整张贴图按 (x, y, fullW, fullH) 拉伸后，只画其中 (cropX, cropY, cropW, cropH) 一块
     * （裁切坐标相对 (x, y)）；进度条按比例显示填充贴图用。
     */
    fun drawTextureCropped(
        graphics: GuiGraphics,
        texture: String,
        x: Int,
        y: Int,
        fullW: Int,
        fullH: Int,
        cropX: Int,
        cropY: Int,
        cropW: Int,
        cropH: Int
    ) {
        if (fullW <= 0 || fullH <= 0 || cropW <= 0 || cropH <= 0) return
        val location = resolve(texture) ?: return
        val left = x + cropX
        val top = y + cropY
        graphics.blit(
            location,
            left, top, left + cropW, top + cropH,
            cropX.toFloat() / fullW, (cropX + cropW).toFloat() / fullW,
            cropY.toFloat() / fullH, (cropY + cropH).toFloat() / fullH
        )
    }

    private fun resolve(texture: String): ResourceLocation? {
        if (texture.isBlank() || texture in invalidPaths) return null
        if (TextureHandler.isHttpUrl(texture)) return TextureHandler.getTexture(texture)
        locations[texture]?.let { return it }
        val parsed = runCatching { IdentifierBridge.parse(texture) }.getOrNull()
        if (parsed == null) {
            invalidPaths.add(texture)
            return null
        }
        locations[texture] = parsed
        return parsed
    }
}
