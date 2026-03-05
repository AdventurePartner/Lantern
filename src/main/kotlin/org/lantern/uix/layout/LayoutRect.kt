package org.lantern.uix.layout

import org.lantern.uix.widget.IWidget
import java.util.IdentityHashMap

/**
 * 单个 widget 的布局计算结果坐标。
 */
data class LayoutRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * 整棵 widget 树的布局结果，使用 IdentityHashMap 以 widget 实例为 key。
 */
class LayoutResult {
    private val rects = IdentityHashMap<IWidget, LayoutRect>()

    fun put(widget: IWidget, rect: LayoutRect) {
        rects[widget] = rect
    }

    fun get(widget: IWidget): LayoutRect? = rects[widget]

    fun contains(widget: IWidget): Boolean = rects.containsKey(widget)

    /** 将所有条目复制到目标 LayoutResult 中。 */
    fun copyTo(target: LayoutResult) {
        target.rects.putAll(rects)
    }

    fun all(): Map<IWidget, LayoutRect> = rects
}
