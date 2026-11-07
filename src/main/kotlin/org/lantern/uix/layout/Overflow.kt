package org.lantern.uix.layout

import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.slot.HotbarSelectionWidgetImpl

/**
 * `overflow: hidden` 的裁切范围：组件自己的区域（[WidgetGeometry.box]），
 * 没写宽高时随内容撑开而不是按 0 把内容全部裁掉。
 */
object Overflow {

    /** hotbar-selection 画在选中槽位上而不在自己的区域里，自身不参与裁切。 */
    fun isHidden(widget: IWidget): Boolean =
        widget !is HotbarSelectionWidgetImpl &&
            widget.style.getString(StyleProperty.OVERFLOW) == "hidden"

    fun contains(widget: IWidget, mouseX: Int, mouseY: Int): Boolean {
        val box = WidgetGeometry.box(widget)
        return mouseX in box.x until box.x + box.width && mouseY in box.y until box.y + box.height
    }
}
