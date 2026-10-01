package org.lantern.uix.widget.vanilla

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.hud.VanillaHudElement
import org.lantern.uix.widget.BaseWidget

/**
 * 原版 HUD 元素的位置声明：HUD 里的 [element] 由原版自己绘制，只是被平移到本节点处
 * （见 VanillaHudPlacement）。节点本身只画 background，便于调位置时看清占位。
 */
class VanillaElementWidgetImpl(
    val element: VanillaHudElement?
) : BaseWidget() {

    override val widgetType: String = "vanilla"

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        // Rendering handled by VanillaElementRenderer via the canvas system
    }
}
