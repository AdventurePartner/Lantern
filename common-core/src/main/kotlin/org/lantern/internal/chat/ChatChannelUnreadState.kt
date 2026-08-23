package org.lantern.internal.chat

class ChatChannelUnreadState(
    private val defaultChannelId: String = "all"
) {
    private val unreadChannelIds = LinkedHashSet<String>()

    fun markUnreadForMessage(
        text: String,
        channels: List<ChatChannel>,
        activeChannelId: String?,
        chatOpen: Boolean
    ) {
        channels.forEach { channel ->
            if (channel.id == defaultChannelId) return@forEach
            if (!ChatChannelRules.matches(text, channel, channels)) return@forEach
            if (chatOpen && channel.id == activeChannelId) return@forEach
            unreadChannelIds.add(channel.id)
        }
    }

    fun markRead(channelId: String) {
        unreadChannelIds.remove(channelId)
    }

    fun retainChannels(channels: List<ChatChannel>) {
        val channelIds = channels.asSequence().map { it.id }.toHashSet()
        unreadChannelIds.removeAll { it !in channelIds }
    }

    fun clear() = unreadChannelIds.clear()

    fun hasUnreadChannel(channelId: String): Boolean = channelId in unreadChannelIds

    fun hasAnyUnread(): Boolean = unreadChannelIds.isNotEmpty()
}
