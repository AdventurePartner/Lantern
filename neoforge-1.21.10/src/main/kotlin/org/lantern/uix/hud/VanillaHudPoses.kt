package org.lantern.uix.hud

import net.minecraft.client.gui.GuiGraphics
import org.lantern.Lantern

/**
 * 原版元素摆放的位姿推入/弹出（1.21.10 Matrix3x2fStack 版，共享源码里是同名 PoseStack 版）。
 * NeoForgeClientEvents 在图层 Pre 调 [begin]、Post 调 [end]；下一图层 Pre 与整帧 Post 调 [popPending]，
 * 兜底被其他模组取消而没走到 [end] 的推入。
 */
object VanillaHudPoses {

    private var warnedPending = false

    @JvmStatic
    fun begin(graphics: GuiGraphics, element: VanillaHudElement, actualX: Int, actualY: Int) {
        val offset = VanillaHudPlacement.offset(
            element, graphics.guiWidth(), graphics.guiHeight(), actualX, actualY
        ) ?: return
        graphics.pose().pushMatrix()
        graphics.pose().translate(offset.dx, offset.dy)
        VanillaHudPlacement.markPushed(element)
    }

    @JvmStatic
    fun end(graphics: GuiGraphics, element: VanillaHudElement) {
        if (VanillaHudPlacement.consumePushed(element)) {
            graphics.pose().popMatrix()
        }
    }

    @JvmStatic
    fun popPending(graphics: GuiGraphics) {
        val pending = VanillaHudPlacement.consumeAllPending()
        if (pending == 0) return
        repeat(pending) { graphics.pose().popMatrix() }
        if (!warnedPending) {
            warnedPending = true
            Lantern.logger.warn(
                "[Lantern] {} 个原版 HUD 元素的摆放位姿未正常复位，可能被其他模组取消了渲染或渲染中出错",
                pending
            )
        }
    }
}
