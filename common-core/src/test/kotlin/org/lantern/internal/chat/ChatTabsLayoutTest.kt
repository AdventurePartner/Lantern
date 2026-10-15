package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTabsLayoutTest {
    private val defaults = ChatTabsStyle()

    @Test
    fun `default layout matches the original hard-coded placement`() {
        val widths = listOf(
            ChatTabsLayout.desiredWidth(10, defaults, null),
            ChatTabsLayout.desiredWidth(28, defaults, null)
        )
        assertEquals(listOf(24, 40), widths)

        val rects = ChatTabsLayout.compute(widths, 400, 300, defaults)
        assertEquals(listOf(ChatTabRect(2, 272, 24, 12), ChatTabRect(28, 272, 40, 12)), rects)
    }

    @Test
    fun `wraps upward when the row is full`() {
        val rects = ChatTabsLayout.compute(listOf(60, 60), 100, 300, defaults)
        assertEquals(listOf(ChatTabRect(2, 272, 60, 12), ChatTabRect(2, 258, 60, 12)), rects)
    }

    @Test
    fun `offsets and per-channel width are applied`() {
        val style = defaults.copy(offsetX = 10, offsetY = -20, height = 16, gap = 4)
        val width = ChatTabsLayout.desiredWidth(100, style, ChatTabOverride(width = 50))
        val rects = ChatTabsLayout.compute(listOf(width), 400, 300, style)
        assertEquals(listOf(ChatTabRect(12, 252, 50, 16)), rects)
    }

    @Test
    fun `clamps a tab wider than the screen`() {
        val rects = ChatTabsLayout.compute(listOf(500), 100, 300, defaults)
        assertEquals(96, rects.single().width)
    }
}
