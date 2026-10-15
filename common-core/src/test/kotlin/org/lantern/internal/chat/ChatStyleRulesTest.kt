package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatStyleRulesTest {
    @Test
    fun `parses six and eight digit colors only`() {
        assertEquals(0xFF202040.toInt(), ChatStyleRules.parseColor("#202040"))
        assertEquals(0x80000000.toInt(), ChatStyleRules.parseColor("#80000000"))
        assertNull(ChatStyleRules.parseColor("#FFF"))
        assertNull(ChatStyleRules.parseColor("red"))
    }

    @Test
    fun `line background multiplies vanilla alpha and replaces rgb`() {
        val vanilla = 0x7F000000
        assertEquals(vanilla, ChatStyleRules.lineBackground(vanilla, null))
        assertEquals(0x7F202040, ChatStyleRules.lineBackground(vanilla, 0xFF202040.toInt()))
        assertEquals(0x3F202040, ChatStyleRules.lineBackground(vanilla, 0x80202040.toInt()))
        assertEquals(0x00202040, ChatStyleRules.lineBackground(0, 0xFF202040.toInt()))
        assertEquals(0, ChatStyleRules.lineBackground(vanilla, 0))
    }

    @Test
    fun `text color keeps vanilla alpha`() {
        val vanilla = 0x40FFFFFF
        assertEquals(vanilla, ChatStyleRules.textColor(vanilla, null))
        assertEquals(0x40E0E0E0, ChatStyleRules.textColor(vanilla, 0xFFE0E0E0.toInt()))
    }

    @Test
    fun `clamps window sizes`() {
        assertEquals(ChatStyleRules.MIN_WIDTH, ChatStyleRules.clampWidth(1))
        assertEquals(320, ChatStyleRules.clampWidth(320))
        assertEquals(ChatStyleRules.MAX_SCALE, ChatStyleRules.clampScale(10.0))
    }
}
