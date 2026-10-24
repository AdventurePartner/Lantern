package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics

/**
 * `overflow: hidden` 的裁切推入/弹出（Matrix3x2fStack 版）。
 * 矩形取当前位姿下的局部坐标；与外层裁切取交集由原版裁切栈完成。
 */
object ScissorClip {

    fun push(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        // 1.21.10 起 enableScissor 自己套用当前位姿，直接传局部坐标
        graphics.enableScissor(x, y, x + width, y + height)
    }

    fun pop(graphics: GuiGraphics) {
        graphics.disableScissor()
    }
}
