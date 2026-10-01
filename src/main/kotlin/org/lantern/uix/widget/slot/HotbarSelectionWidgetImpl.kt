package org.lantern.uix.widget.slot

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.widget.BaseWidget

/**
 * 快捷栏选中框：跟随同一棵树里 `source` 为当前选中快捷栏格的 slot 节点，
 * style 的 x/y 是相对该 slot 左上角的偏移，width/height 为选中框尺寸。
 */
class HotbarSelectionWidgetImpl(
    val texture: String = ""
) : BaseWidget() {

    override val widgetType: String = "hotbar-selection"

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        // Rendering handled by HotbarSelectionRenderer via the canvas system
    }
}
