package org.lantern.uix.hud

import net.minecraft.client.gui.GuiGraphics
import org.lantern.Lantern

/**
 * 原版元素摆放的位姿推入/弹出（PoseStack 版：Fabric/Forge 1.21.1 与 Forge 1.20.1 共用；
 * NeoForge 1.21.10 模块有同名的 Matrix3x2fStack 副本）。
 * 平台挂钩在元素绘制前调 [begin]、绘制后调 [end]；一帧结束前调 [popPending]，
 * 兜底被其他模组取消或异常打断而没走到 [end] 的推入。
 */
object VanillaHudPoses {

    private var warnedPending = false

    @JvmStatic
    fun begin(graphics: GuiGraphics, element: VanillaHudElement, actualX: Int, actualY: Int) {
        val offset = VanillaHudPlacement.offset(
            element, graphics.guiWidth(), graphics.guiHeight(), actualX, actualY
        ) ?: return
        graphics.pose().pushPose()
        graphics.pose().translate(offset.dx, offset.dy, 0f)
        VanillaHudPlacement.markPushed(element)
    }

    @JvmStatic
    fun end(graphics: GuiGraphics, element: VanillaHudElement) {
        if (VanillaHudPlacement.consumePushed(element)) {
            graphics.pose().popPose()
        }
    }

    @JvmStatic
    fun popPending(graphics: GuiGraphics) {
        val pending = VanillaHudPlacement.consumeAllPending()
        if (pending == 0) return
        repeat(pending) { graphics.pose().popPose() }
        if (!warnedPending) {
            warnedPending = true
            Lantern.logger.warn(
                "[Lantern] {} 个原版 HUD 元素的摆放位姿未正常复位，可能被其他模组取消了渲染或渲染中出错",
                pending
            )
        }
    }
}
