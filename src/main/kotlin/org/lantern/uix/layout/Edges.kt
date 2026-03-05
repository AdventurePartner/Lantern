package org.lantern.uix.layout

import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule

/**
 * 四方向边距辅助类，用于解析 padding 和 margin。
 * 简写属性为统一值，具体方向属性可覆盖。
 */
data class Edges(
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0
) {
    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom

    companion object {
        fun parsePadding(style: StyleRule): Edges {
            val base = style.getInt(StyleProperty.PADDING)
            return Edges(
                top = style.getInt(StyleProperty.PADDING_TOP, base),
                right = style.getInt(StyleProperty.PADDING_RIGHT, base),
                bottom = style.getInt(StyleProperty.PADDING_BOTTOM, base),
                left = style.getInt(StyleProperty.PADDING_LEFT, base)
            )
        }

        fun parseMargin(style: StyleRule): Edges {
            val base = style.getInt(StyleProperty.MARGIN)
            return Edges(
                top = style.getInt(StyleProperty.MARGIN_TOP, base),
                right = style.getInt(StyleProperty.MARGIN_RIGHT, base),
                bottom = style.getInt(StyleProperty.MARGIN_BOTTOM, base),
                left = style.getInt(StyleProperty.MARGIN_LEFT, base)
            )
        }
    }
}
