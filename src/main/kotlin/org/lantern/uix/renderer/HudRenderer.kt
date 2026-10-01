package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.internal.storage.HudLayer
import org.lantern.internal.storage.UiScreenStorage

object HudRenderer {

    /**
     * 原版层（index < 0）：由平台挂钩在原版相机覆盖层之后、准星/快捷栏之前调用。
     * 可见性跟随原版 HUD——聊天、背包、箱子打开时依然绘制在界面后面。
     */
    fun renderBase(graphics: GuiGraphics) {
        if (!inWorld()) return
        val roots = UiScreenStorage.hudRoots(HudLayer.BASE)
        if (roots.isEmpty()) return
        CanvasRenderer.hudCanvas.renderLayer(graphics, roots)
    }

    /** 顶层（index >= 0）：画在全部原版 HUD 之上，任何界面打开时隐藏。 */
    fun renderTop(graphics: GuiGraphics) {
        if (!inWorld() || Minecraft.getInstance().screen != null) return
        val roots = UiScreenStorage.hudRoots(HudLayer.TOP)
        if (roots.isEmpty()) return
        CanvasRenderer.hudCanvas.renderLayer(graphics, roots)
    }

    private fun inWorld(): Boolean {
        val client = Minecraft.getInstance()
        if (client.options.hideGui) return false
        return client.player != null && client.level != null
    }
}
