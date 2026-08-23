package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatChannelRulesTest {
    @Test
    fun `normalizes channels and filters duplicate ids`() {
        val normalized = ChatChannelRules.normalizeChannels(
            listOf(
                ChatChannel(" announcement ", " 公告 ", listOf(" [公告] ", "[公告]", ""), listOf(" system ")),
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
    fun `matches prefixes and referenced filters`() {
        val all = ChatChannel("all", "全部", emptyList(), listOf("system"))
        val system = ChatChannel("system", "系统", listOf("[系统]"))
        val channels = listOf(all, system)

        assertTrue(ChatChannelRules.matches("普通消息", all, channels))
        assertFalse(ChatChannelRules.matches("[系统]重启", all, channels))
        assertTrue(ChatChannelRules.matches("[系统]重启", system, channels))
    }
}
