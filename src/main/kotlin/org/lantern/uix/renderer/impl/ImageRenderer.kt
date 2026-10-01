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
    private val rlCache = ConcurrentHashMap<String, ResourceLocation>()
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
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, 16)
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, 16)
        BackgroundPainter.paint(graphics, style, x, y, w, h)
        drawTexture(graphics, widget.texture, x, y, w, h)
    }

    /** 整张贴图拉伸画到 (x, y, w, h)；texture 为资源路径或 http(s) URL。 */
    fun drawTexture(graphics: GuiGraphics, texture: String, x: Int, y: Int, w: Int, h: Int) {
        val loc = resolve(texture) ?: return
        graphics.blit(loc, x, y, w, h, 0f, 0f, w, h, w, h)
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
        val loc = resolve(texture) ?: return
        graphics.blit(loc, x + cropX, y + cropY, cropW, cropH, cropX.toFloat(), cropY.toFloat(), cropW, cropH, fullW, fullH)
    }

    private fun resolve(texture: String): ResourceLocation? {
        if (texture.isBlank() || texture in invalidPaths) return null
        if (TextureHandler.isHttpUrl(texture)) return TextureHandler.getTexture(texture)
        rlCache[texture]?.let { return it }
        val parsed = try { IdentifierBridge.parse(texture) } catch (_: Exception) { null }
        if (parsed == null) {
            invalidPaths.add(texture)
            return null
        }
        rlCache[texture] = parsed
        return parsed
    }
}
