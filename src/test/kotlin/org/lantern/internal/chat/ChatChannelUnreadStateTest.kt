package org.lantern.internal.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatChannelUnreadStateTest {
    @Test
    fun `marks non default matching channel unread when chat is closed`() {
        val unreadState = ChatChannelUnreadState()
        val all = ChatChannel("all", "全部", emptyList(), listOf("system"))
        val system = ChatChannel("system", "系统", listOf("[系统]"))
        val channels = listOf(all, system)

        unreadState.markUnreadForMessage("[系统]重启", channels, activeChannelId = "all", chatOpen = false)

        assertTrue(unreadState.hasAnyUnread())
        assertTrue(unreadState.hasUnreadChannel("system"))
        assertFalse(unreadState.hasUnreadChannel("all"))
    }

    @Test
    fun `does not mark active channel unread while chat is open`() {
        val unreadState = ChatChannelUnreadState()
        val system = ChatChannel("system", "系统", listOf("[系统]"))

        unreadState.markUnreadForMessage("[系统]重启", listOf(system), activeChannelId = "system", chatOpen = true)

        assertFalse(unreadState.hasUnreadChannel("system"))
    }

    @Test
    fun `marks inactive matching channel unread while chat is open`() {
        val unreadState = ChatChannelUnreadState()
        val all = ChatChannel("all", "全部", emptyList(), listOf("system"))
        val system = ChatChannel("system", "系统", listOf("[系统]"))
        val channels = listOf(all, system)

        unreadState.markUnreadForMessage("[系统]重启", channels, activeChannelId = "all", chatOpen = true)

        assertTrue(unreadState.hasUnreadChannel("system"))
    }

    @Test
    fun `mark read clears channel unread state`() {
        val unreadState = ChatChannelUnreadState()
        val system = ChatChannel("system", "系统", listOf("[系统]"))

        unreadState.markUnreadForMessage("[系统]重启", listOf(system), activeChannelId = null, chatOpen = false)
        unreadState.markRead("system")

        assertFalse(unreadState.hasAnyUnread())
    }

    @Test
    fun `retain channels removes unread ids that no longer exist`() {
        val unreadState = ChatChannelUnreadState()
        val system = ChatChannel("system", "系统", listOf("[系统]"))

        unreadState.markUnreadForMessage("[系统]重启", listOf(system), activeChannelId = null, chatOpen = false)
        unreadState.retainChannels(emptyList())

        assertFalse(unreadState.hasAnyUnread())
    }
}
