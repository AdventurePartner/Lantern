package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatChannelMessageCacheTest {
    @Test
    fun `stores newest messages and trims the oldest`() {
        val cache = ChatChannelMessageCache(3) { value: String -> value }
        listOf("one", "two", "three", "four").forEach(cache::add)

        assertEquals(listOf("four", "three", "two"), cache.allMessages())
    }

    @Test
    fun `filters visible messages by channel`() {
        val cache = ChatChannelMessageCache(100) { value: String -> value }
        val channel = ChatChannel("announcement", "公告", listOf("[公告]"))
        cache.add("普通消息")
        cache.add("[公告]维护")

        assertEquals(listOf("[公告]维护"), cache.visibleMessages(channel, listOf(channel)))
    }
}
