package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.renderer.impl.ImageRenderer
import org.lantern.uix.style.Background
import org.lantern.uix.style.StyleRule

object BackgroundPainter {

    /** 按 style 的 `background` 画组件背景；未配置时画该组件的 [default]。 */
    fun paint(
        graphics: GuiGraphics,
        style: StyleRule,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        default: Background = Background.None
    ) {
        if (width <= 0 || height <= 0) return
        paint(graphics, Background.resolve(style, default), x, y, width, height)
    }

    fun paint(graphics: GuiGraphics, background: Background, x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        when (background) {
            Background.None -> Unit
            is Background.Color -> graphics.fill(x, y, x + width, y + height, background.argb)
            is Background.Image -> ImageRenderer.drawTexture(graphics, background.texture, x, y, width, height)
            is Background.Custom -> background.draw(graphics, x, y, width, height)
        }
    }
}
