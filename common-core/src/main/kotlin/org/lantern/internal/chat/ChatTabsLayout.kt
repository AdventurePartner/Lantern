package org.lantern.internal.chat

import kotlin.math.max

data class ChatTabRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int): Boolean =
        px >= x && py >= y && px < x + width && py < y + height
}

/**
 * 频道按钮排布：从输入框上方左侧起横向排列，放不下时换到上一行。
 * 偏移平移的是整组按钮，换行边界仍是屏幕右缘。
 */
object ChatTabsLayout {
    private const val EDGE = 2
    private const val INPUT_TOP_OFFSET = 28

    fun desiredWidth(textWidth: Int, style: ChatTabsStyle, override: ChatTabOverride?): Int {
        override?.width?.let { return it.coerceAtLeast(1) }
        return max(textWidth + style.paddingX * 2, style.minWidth)
    }

    fun compute(widths: List<Int>, guiWidth: Int, guiHeight: Int, style: ChatTabsStyle): List<ChatTabRect> {
        if (widths.isEmpty()) return emptyList()

        val height = style.height.coerceAtLeast(1)
        val gap = style.gap.coerceAtLeast(0)
        val startX = EDGE + style.offsetX
        val right = guiWidth - EDGE
        val maxWidth = (right - startX).coerceAtLeast(style.minWidth.coerceAtLeast(1))

        val rects = ArrayList<ChatTabRect>(widths.size)
        var x = startX
        var y = guiHeight - INPUT_TOP_OFFSET + style.offsetY
        widths.forEach { desired ->
            val width = desired.coerceAtMost(maxWidth)
            if (x != startX && x + width > right) {
                x = startX
                y -= height + gap
            }
            rects.add(ChatTabRect(x, y, width, height))
            x += width + gap
        }
        return rects
    }
}
