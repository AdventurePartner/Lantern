package org.lantern.uix.layout

import org.lantern.uix.widget.IWidget
import java.util.IdentityHashMap

/**
 * 全局布局缓存。
 * 对每棵 widget 树懒计算并缓存布局结果，同时维护一个
 * 扁平索引以便任意 widget 可以快速查找其坐标。
 */
object LayoutCache {

    // Pack width+height into a Long for zero-allocation cache key
    private fun packKey(width: Int, height: Int): Long =
        (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)

    private val cache = IdentityHashMap<IWidget, Pair<Long, LayoutResult>>()
    private val flatIndex = IdentityHashMap<IWidget, LayoutRect>()

    /**
     * 获取或计算指定根 widget 的布局结果。
     * 仅在尺寸变化或缓存失效时重新计算。
     */
    fun getOrCompute(root: IWidget, width: Int, height: Int): LayoutResult {
        val key = packKey(width, height)
        val existing = cache[root]
        if (existing != null && existing.first == key) {
            return existing.second
        }

        val result = LayoutEngine.layout(root, width, height)
        cache[root] = key to result

        // 更新扁平索引
        result.all().forEach { (widget, rect) ->
            flatIndex[widget] = rect
        }

        return result
    }

    /**
     * 从扁平索引快速查找任意 widget 的布局坐标。
     */
    fun findRect(widget: IWidget): LayoutRect? = flatIndex[widget]

    /**
     * 使指定根 widget 的缓存失效。
     */
    fun invalidate(root: IWidget) {
        val existing = cache.remove(root)
        existing?.second?.all()?.keys?.forEach { flatIndex.remove(it) }
    }

    /**
     * 清除所有缓存（配置更新或窗口缩放时调用）。
     */
    fun clear() {
        cache.clear()
        flatIndex.clear()
    }
}
