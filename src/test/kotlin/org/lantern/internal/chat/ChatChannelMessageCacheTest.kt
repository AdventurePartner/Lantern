package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatChannelMessageCacheTest {
    @Test
    fun `add stores newest first`() {
        val cache = ChatChannelMessageCache(100) { it: String -> it }

        cache.add("first")
        cache.add("second")

        assertEquals(listOf("second", "first"), cache.allMessages())
    }

    @Test
    fun `add trims oldest messages after max size`() {
        val cache = ChatChannelMessageCache(3) { it: String -> it }

        cache.add("one")
        cache.add("two")
        cache.add("three")
        cache.add("four")

        assertEquals(listOf("four", "three", "two"), cache.allMessages())
    }

    @Test
    fun `visible messages filters by channel prefixes`() {
        val cache = ChatChannelMessageCache(100) { it: String -> it }
        val channel = ChatChannel("announcement", "公告", listOf("[公告]"))

        cache.add("普通消息")
        cache.add("[公告]维护")
        cache.add("玩家[公告]维护")

        assertEquals(listOf("[公告]维护"), cache.visibleMessages(channel, listOf(channel)))
    }

    @Test
    fun `empty prefix channel returns all messages`() {
        val cache = ChatChannelMessageCache(100) { it: String -> it }
        val channel = ChatChannel("all", "全部", emptyList())

        cache.add("普通消息")
        cache.add("[公告]维护")

        assertEquals(listOf("[公告]维护", "普通消息"), cache.visibleMessages(channel, listOf(channel)))
    }


    @Test
    fun `visible messages excludes filtered channel prefixes`() {
        val cache = ChatChannelMessageCache(100) { it: String -> it }
        val all = ChatChannel("all", "全部", emptyList(), listOf("system"))
        val system = ChatChannel("system", "系统", listOf("[系统]"))
        val channels = listOf(all, system)

        cache.add("普通消息")
        cache.add("[系统]重启")
        cache.add("[公告]维护")

        assertEquals(listOf("[公告]维护", "普通消息"), cache.visibleMessages(all, channels))
    }
    @Test
    fun `seed if empty imports only once`() {
        val cache = ChatChannelMessageCache(100) { it: String -> it }

        cache.seedIfEmpty(listOf("old two", "old one"))
        cache.seedIfEmpty(listOf("new"))

        assertEquals(listOf("old two", "old one"), cache.allMessages())
    }
}
