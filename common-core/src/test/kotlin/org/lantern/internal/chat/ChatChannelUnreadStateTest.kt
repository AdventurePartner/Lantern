package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatChannelUnreadStateTest {
    @Test
    fun `tracks unread channels and removes stale ids`() {
        val state = ChatChannelUnreadState()
        val system = ChatChannel("system", "系统", listOf("[系统]"))

        state.markUnreadForMessage("[系统]重启", listOf(system), activeChannelId = null, chatOpen = false)
        assertTrue(state.hasUnreadChannel("system"))

        state.retainChannels(emptyList())
        assertFalse(state.hasAnyUnread())
    }
}
