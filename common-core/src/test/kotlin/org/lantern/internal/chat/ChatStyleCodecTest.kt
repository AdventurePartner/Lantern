package org.lantern.internal.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatStyleCodecTest {
    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    @Test
    fun `missing style keeps defaults`() {
        assertEquals(ChatStyle.DEFAULT, ChatStyleCodec.parse(null))
        assertEquals(ChatStyle.DEFAULT, ChatStyleCodec.parse(json("{}")))
    }

    @Test
    fun `parses window section with clamping and none`() {
        val style = ChatStyleCodec.parse(
            json(
                """{"window":{"width":5000,"height":180,"scale":"1.5","offset-x":4,"offset-y":-20,
                   "background":"none","text-color":"#E0E0E0","input-background":"#80000000"}}"""
            )
        )
        assertEquals(
            ChatWindowStyle(
                width = ChatStyleRules.MAX_WIDTH,
                height = 180,
                scale = 1.5,
                offsetX = 4,
                offsetY = -20,
                background = 0,
                textColor = 0xFFE0E0E0.toInt(),
                inputBackground = 0x80000000.toInt()
            ),
            style.window
        )
    }

    @Test
    fun `parses tabs section and keeps defaults for bad values`() {
        val tabs = ChatStyleCodec.parse(
            json(
                """{"tabs":{"height":16,"text-align":"center","text-shadow":true,"background":"url(chat/tab.png)",
                   "hover-outline":"none","text-color":"oops","unread-size":"x"}}"""
            )
        ).tabs
        val defaults = ChatTabsStyle()
        assertEquals(16, tabs.height)
        assertEquals(ChatTextAlign.CENTER, tabs.textAlign)
        assertEquals(true, tabs.textShadow)
        assertEquals("url(chat/tab.png)", tabs.background)
        assertNull(tabs.hoverOutline)
        assertEquals(defaults.textColor, tabs.textColor)
        assertEquals(defaults.unreadSize, tabs.unreadSize)
        assertEquals(defaults.selectedBackground, tabs.selectedBackground)
    }

    @Test
    fun `parses notification section`() {
        val notification = ChatStyleCodec.parse(
            json("""{"notification":{"enabled":false,"text":"§e新消息","offset-y":-30}}""")
        ).notification
        assertEquals(ChatNotificationStyle(enabled = false, text = "§e新消息", offsetY = -30), notification)
    }

    @Test
    fun `tab override is null when empty`() {
        assertNull(ChatStyleCodec.parseTabOverride(json("{}"), "all"))
        assertEquals(
            ChatTabOverride(width = 40, selectedBackground = "#C0D9904A"),
            ChatStyleCodec.parseTabOverride(json("""{"width":40,"selected-background":"#C0D9904A"}"""), "all")
        )
    }
}
