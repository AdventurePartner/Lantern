package org.lantern.uix.layout

import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget

/**
 * 组件在 GUI 坐标系里的绝对位置：根节点的布局 rect 是绝对坐标，子组件 rect 相对父容器，
 * 未参与布局计算的组件取 style 的 x/y——沿祖先链累加，与 PanelRenderer 的逐层平移一致。
 * 调用前所在的树需已经过 [LayoutCache] 布局。
 */
object WidgetGeometry {

    fun absoluteX(widget: IWidget): Int {
        var sum = 0
        var current: IWidget? = widget
        while (current != null) {
            sum += LayoutCache.findRect(current)?.x ?: current.style.getInt(StyleProperty.X)
            current = current.parent as? IWidget
        }
        return sum
    }

    fun absoluteY(widget: IWidget): Int {
        var sum = 0
        var current: IWidget? = widget
        while (current != null) {
            sum += LayoutCache.findRect(current)?.y ?: current.style.getInt(StyleProperty.Y)
            current = current.parent as? IWidget
        }
        return sum
    }

    /** 布局宽度；未参与布局时取 style 的 width，都没有时用 [default]。 */
    fun width(widget: IWidget, default: Int): Int =
        LayoutCache.findRect(widget)?.width
            ?: widget.style.getInt(StyleProperty.WIDTH).takeIf { it > 0 }
            ?: default

    fun height(widget: IWidget, default: Int): Int =
        LayoutCache.findRect(widget)?.height
            ?: widget.style.getInt(StyleProperty.HEIGHT).takeIf { it > 0 }
            ?: default
}
