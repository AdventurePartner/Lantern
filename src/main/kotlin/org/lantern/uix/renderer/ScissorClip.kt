package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * `overflow: hidden` 的裁切推入/弹出（PoseStack 版：Fabric 1.21.1、NeoForge 1.21.1 与 Forge 1.20.1 共用；
 * NeoForge 1.21.10 / 1.21.11 模块有同名的 Matrix3x2fStack 副本）。
 * 矩形取当前位姿下的局部坐标；与外层裁切取交集由原版裁切栈完成。
 */
object ScissorClip {

    fun push(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        // 这两个版本的 enableScissor 不套用位姿，要自己换算成屏幕坐标
        val pose = graphics.pose().last().pose()
        val a = pose.transformPosition(x.toFloat(), y.toFloat(), 0f, Vector3f())
        val b = pose.transformPosition((x + width).toFloat(), (y + height).toFloat(), 0f, Vector3f())
        graphics.enableScissor(
            min(a.x, b.x).roundToInt(),
            min(a.y, b.y).roundToInt(),
            max(a.x, b.x).roundToInt(),
            max(a.y, b.y).roundToInt()
        )
    }

    fun pop(graphics: GuiGraphics) {
        graphics.disableScissor()
    }
}
