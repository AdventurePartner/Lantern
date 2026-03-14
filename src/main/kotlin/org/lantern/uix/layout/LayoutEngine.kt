package org.lantern.uix.layout

import org.lantern.uix.enums.AlignItems
import org.lantern.uix.enums.Anchor
import org.lantern.uix.enums.FlexDirection
import org.lantern.uix.enums.JustifyContent
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.panel.PanelWidgetImpl

/**
 * 无状态布局引擎核心。
 * 递归遍历 widget 树，对声明了 flex 布局属性的 Panel 执行 flex 排列，
 * 对声明了 position: absolute + anchor 的子组件执行锚点定位。
 * 未使用布局属性的 Panel 跳过计算，保持向后兼容。
 */
object LayoutEngine {

    fun layout(root: IWidget, availableWidth: Int, availableHeight: Int): LayoutResult {
        val result = LayoutResult()
        // 将根 widget 放入布局结果，使渲染器能查到它的坐标
        if (root is PanelWidgetImpl) {
            val rootW = root.style.getInt(StyleProperty.WIDTH).let { if (it == 0) availableWidth else it }
            val rootH = root.style.getInt(StyleProperty.HEIGHT).let { if (it == 0) availableHeight else it }
            result.put(root, LayoutRect(0, 0, rootW, rootH))
        }
        layoutWidget(root, 0, 0, availableWidth, availableHeight, result)
        return result
    }

    private fun layoutWidget(
        widget: IWidget,
        parentX: Int,
        parentY: Int,
        availableWidth: Int,
        availableHeight: Int,
        result: LayoutResult
    ) {
        if (widget !is PanelWidgetImpl) return

        val style = widget.style

        // 向后兼容判断：如果没有任何布局属性，跳过布局计算
        val hasLayoutProps = style.get(StyleProperty.DISPLAY) != null ||
                style.get(StyleProperty.FLEX_DIRECTION) != null ||
                style.get(StyleProperty.JUSTIFY_CONTENT) != null ||
                style.get(StyleProperty.ALIGN_ITEMS) != null

        if (!hasLayoutProps) {
            // 仅递归处理子容器
            widget.children.forEach { child ->
                if (child is PanelWidgetImpl) {
                    val cx = child.style.getInt(StyleProperty.X)
                    val cy = child.style.getInt(StyleProperty.Y)
                    val cw = child.style.getInt(StyleProperty.WIDTH)
                    val ch = child.style.getInt(StyleProperty.HEIGHT)
                    layoutWidget(child, parentX + cx, parentY + cy, cw, ch, result)
                }
            }
            return
        }

        // 解析容器自身尺寸（0 或未设置时使用可用空间）
        val containerW = style.getInt(StyleProperty.WIDTH).let { if (it == 0) availableWidth else it }
        val containerH = style.getInt(StyleProperty.HEIGHT).let { if (it == 0) availableHeight else it }

        // 解析 padding
        val padding = Edges.parsePadding(style)
        val contentX = padding.left
        val contentY = padding.top
        val contentW = containerW - padding.horizontal
        val contentH = containerH - padding.vertical

        // 解析 flex 属性
        val direction = style.getString(StyleProperty.FLEX_DIRECTION, "column")
            .let { FlexDirection.fromKey(it) } ?: FlexDirection.COLUMN
        val justify = style.getString(StyleProperty.JUSTIFY_CONTENT, "start")
            .let { JustifyContent.fromKey(it) } ?: JustifyContent.START
        val align = style.getString(StyleProperty.ALIGN_ITEMS, "start")
            .let { AlignItems.fromKey(it) } ?: AlignItems.START
        val gap = style.getInt(StyleProperty.GAP)

        // 分离 flex 子项和 absolute 子项
        val flexChildren = mutableListOf<IWidget>()
        val absoluteChildren = mutableListOf<IWidget>()

        widget.children.forEach { child ->
            val posStr = child.style.getString(StyleProperty.POSITION, "")
            if (posStr == "absolute") {
                absoluteChildren.add(child)
            } else {
                flexChildren.add(child)
            }
        }

        // Flex 布局
        layoutFlexChildren(
            flexChildren, direction, justify, align, gap,
            contentX, contentY, contentW, contentH, result
        )

        // Absolute 布局
        absoluteChildren.forEach { child ->
            layoutAbsoluteChild(child, containerW, containerH, result)
        }

        // 递归处理子容器
        widget.children.forEach { child ->
            if (child is PanelWidgetImpl) {
                val rect = result.get(child)
                if (rect != null) {
                    layoutWidget(child, rect.x, rect.y, rect.width, rect.height, result)
                }
            }
        }
    }

    private fun layoutFlexChildren(
        children: List<IWidget>,
        direction: FlexDirection,
        justify: JustifyContent,
        align: AlignItems,
        gap: Int,
        contentX: Int,
        contentY: Int,
        contentW: Int,
        contentH: Int,
        result: LayoutResult
    ) {
        if (children.isEmpty()) return

        val isRow = direction == FlexDirection.ROW

        // 计算每个子项在主轴方向上的尺寸
        data class ChildSize(val main: Int, val cross: Int, val w: Int, val h: Int)

        val sizes = children.map { child ->
            val cw = child.style.getInt(StyleProperty.WIDTH)
            val ch = child.style.getInt(StyleProperty.HEIGHT)
            if (isRow) ChildSize(cw, ch, cw, ch) else ChildSize(ch, cw, cw, ch)
        }

        val totalMain = sizes.sumOf { it.main }
        val totalGap = gap * (children.size - 1).coerceAtLeast(0)
        val mainSpace = if (isRow) contentW else contentH
        val crossSpace = if (isRow) contentH else contentW
        val freeSpace = (mainSpace - totalMain - totalGap).coerceAtLeast(0)

        // 根据 justify-content 计算主轴起始偏移和间距
        var mainOffset: Int
        val extraGap: Int

        when (justify) {
            JustifyContent.START -> {
                mainOffset = 0
                extraGap = 0
            }
            JustifyContent.CENTER -> {
                mainOffset = freeSpace / 2
                extraGap = 0
            }
            JustifyContent.END -> {
                mainOffset = freeSpace
                extraGap = 0
            }
            JustifyContent.SPACE_BETWEEN -> {
                mainOffset = 0
                extraGap = if (children.size > 1) freeSpace / (children.size - 1) else 0
            }
            JustifyContent.SPACE_EVENLY -> {
                val slots = children.size + 1
                val evenSpace = freeSpace / slots
                mainOffset = evenSpace
                extraGap = evenSpace
            }
        }

        children.forEachIndexed { index, child ->
            val size = sizes[index]
            val childMargin = Edges.parseMargin(child.style)

            // 交叉轴对齐
            val crossOffset = when (align) {
                AlignItems.START -> 0
                AlignItems.CENTER -> (crossSpace - size.cross) / 2
                AlignItems.END -> crossSpace - size.cross
                AlignItems.STRETCH -> 0
            }

            val stretchCross = if (align == AlignItems.STRETCH) crossSpace else size.cross

            val x: Int
            val y: Int
            val w: Int
            val h: Int

            if (isRow) {
                x = contentX + mainOffset + childMargin.left
                y = contentY + crossOffset + childMargin.top
                w = size.w
                h = if (align == AlignItems.STRETCH && size.h == 0) stretchCross - childMargin.vertical else size.h
            } else {
                x = contentX + crossOffset + childMargin.left
                y = contentY + mainOffset + childMargin.top
                w = if (align == AlignItems.STRETCH && size.w == 0) stretchCross - childMargin.horizontal else size.w
                h = size.h
            }

            result.put(child, LayoutRect(x, y, w, h))
            mainOffset += size.main + gap + extraGap + if (isRow) childMargin.horizontal else childMargin.vertical
        }
    }

    private fun layoutAbsoluteChild(
        child: IWidget,
        containerW: Int,
        containerH: Int,
        result: LayoutResult
    ) {
        val style = child.style
        val margin = Edges.parseMargin(style)
        val w = style.getInt(StyleProperty.WIDTH)
        val h = style.getInt(StyleProperty.HEIGHT)

        val anchorStr = style.getString(StyleProperty.ANCHOR, "top-left")
        val anchor = Anchor.fromKey(anchorStr) ?: Anchor.TOP_LEFT

        // 根据 anchor 的 3×3 网格计算锚点坐标
        val anchorX: Int
        val anchorY: Int

        when (anchor) {
            Anchor.TOP_LEFT -> {
                anchorX = margin.left
                anchorY = margin.top
            }
            Anchor.TOP_CENTER -> {
                anchorX = (containerW - w) / 2
                anchorY = margin.top
            }
            Anchor.TOP_RIGHT -> {
                anchorX = containerW - w - margin.right
                anchorY = margin.top
            }
            Anchor.CENTER_LEFT -> {
                anchorX = margin.left
                anchorY = (containerH - h) / 2
            }
            Anchor.CENTER -> {
                anchorX = (containerW - w) / 2
                anchorY = (containerH - h) / 2
            }
            Anchor.CENTER_RIGHT -> {
                anchorX = containerW - w - margin.right
                anchorY = (containerH - h) / 2
            }
            Anchor.BOTTOM_LEFT -> {
                anchorX = margin.left
                anchorY = containerH - h - margin.bottom
            }
            Anchor.BOTTOM_CENTER -> {
                anchorX = (containerW - w) / 2
                anchorY = containerH - h - margin.bottom
            }
            Anchor.BOTTOM_RIGHT -> {
                anchorX = containerW - w - margin.right
                anchorY = containerH - h - margin.bottom
            }
        }

        result.put(child, LayoutRect(anchorX, anchorY, w, h))
    }
}
