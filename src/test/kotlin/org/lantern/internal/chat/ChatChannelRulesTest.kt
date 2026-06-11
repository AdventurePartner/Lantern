package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatChannelRulesTest {
    @Test
    fun `normalize prefixes trims drops blank and keeps first distinct prefix`() {
        val normalized = ChatChannelRules.normalizePrefixes(listOf(" [公告] ", "", "[系统]", "[公告]"))

        assertEquals(listOf("[公告]", "[系统]"), normalized)
    }

    @Test
    fun `normalize channels drops blank ids keeps first id and normalizes display name and prefixes`() {
        val normalized = ChatChannelRules.normalizeChannels(
            listOf(
                ChatChannel(" announcement ", " 公告 ", listOf(" [公告] ", "[公告]", ""), listOf(" system ", "", "system")),
                ChatChannel("", "空", listOf("[空]")),
                ChatChannel("announcement", "重复", listOf("[重复]")),
                ChatChannel(" system ", " ", listOf("[系统]"))
            )
        )

        assertEquals(
            listOf(
                ChatChannel("announcement", "公告", listOf("[公告]"), listOf("system")),
                ChatChannel("system", "system", listOf("[系统]"))
            ),
            normalized
        )
    }

    @Test
    fun `empty prefix channel matches all text`() {
        val channel = ChatChannel("all", "全部", emptyList())

        assertTrue(ChatChannelRules.matches("普通消息", channel, listOf(channel)))
        assertTrue(ChatChannelRules.matches("[公告]维护", channel, listOf(channel)))
    }

    @Test
    fun `non empty prefixes only match text start`() {
        val channel = ChatChannel("announcement", "公告", listOf("[公告]", "[系统]"))

        assertTrue(ChatChannelRules.matches("[公告]维护", channel, listOf(channel)))
        assertFalse(ChatChannelRules.matches("玩家[公告]维护", channel, listOf(channel)))
        assertFalse(ChatChannelRules.matches("[其它]维护", channel, listOf(channel)))
    }

    @Test
    fun `filter excludes messages matching referenced channel prefixes`() {
        val all = ChatChannel("all", "全部", emptyList(), listOf("system"))
        val system = ChatChannel("system", "系统", listOf("[系统]"))
        val channels = listOf(all, system)

        assertTrue(ChatChannelRules.matches("普通消息", all, channels))
        assertFalse(ChatChannelRules.matches("[系统]重启", all, channels))
    }

    @Test
    fun `filter ignores referenced channels with empty prefixes`() {
        val all = ChatChannel("all", "全部", emptyList(), listOf("archive"))
        val archive = ChatChannel("archive", "归档", emptyList())
        val channels = listOf(all, archive)

        assertTrue(ChatChannelRules.matches("普通消息", all, channels))
    }
}
