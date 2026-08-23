package org.lantern.internal.chat

import net.minecraft.client.GuiMessage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen

object ChatChannelHandler {
    private const val MAX_MESSAGES = 100

    private val messageCache = ChatChannelMessageCache<GuiMessage>(MAX_MESSAGES) { it.content.string }
    private val unreadState = ChatChannelUnreadState()
    private var channels: List<ChatChannel> = emptyList()
    private var activeChannelId: String? = null

    fun setChannels(rawChannels: Iterable<ChatChannel>) {
        val normalizedChannels = ChatChannelRules.normalizeChannels(rawChannels)
        val chat = Minecraft.getInstance().gui.chat

        if (normalizedChannels.isEmpty()) {
            if (channels.isNotEmpty() && !messageCache.isEmpty()) {
                applyMessages(chat, messageCache.allMessages())
            }
            channels = emptyList()
            activeChannelId = null
            messageCache.clear()
            unreadState.clear()
            return
        }

        if (messageCache.isEmpty()) {
            seedCurrentMessages(chat)
        }

        val previousChannelId = activeChannelId
        channels = normalizedChannels
        activeChannelId = if (previousChannelId != null && channels.any { it.id == previousChannelId }) {
            previousChannelId
        } else {
            channels.first().id
        }
        unreadState.retainChannels(channels)
        applyActiveChannel(chat)
    }

    fun clear() {
        channels = emptyList()
        activeChannelId = null
        messageCache.clear()
        unreadState.clear()
    }

    fun channels(): List<ChatChannel> {
        return channels
    }

    fun activeChannelId(): String? {
        return activeChannelId
    }

    fun hasUnreadChannel(channelId: String): Boolean {
        return unreadState.hasUnreadChannel(channelId)
    }

    fun hasUnreadMessages(): Boolean {
        return unreadState.hasAnyUnread()
    }

    fun markActiveChannelRead() {
        unreadState.markRead(activeChannelId ?: return)
    }

    fun capture(message: GuiMessage): Boolean {
        if (channels.isEmpty()) {
            return false
        }

        messageCache.add(message)
        val text = message.content.string
        unreadState.markUnreadForMessage(text, channels, activeChannelId, Minecraft.getInstance().screen is ChatScreen)
        val channel = activeChannel() ?: return false
        return !ChatChannelRules.matches(text, channel, channels)
    }

    fun selectChannel(id: String): Boolean {
        if (channels.none { it.id == id }) {
            return false
        }
        unreadState.markRead(id)
        if (activeChannelId == id) {
            return true
        }

        activeChannelId = id
        applyActiveChannel(Minecraft.getInstance().gui.chat)
        return true
    }

    private fun activeChannel(): ChatChannel? {
        val id = activeChannelId ?: return null
        return channels.firstOrNull { it.id == id }
    }

    private fun seedCurrentMessages(chat: ChatComponent) {
        val accessor = chat as ChatComponentBridge
        messageCache.seedIfEmpty(accessor.`lantern$getAllMessages`())
    }

    private fun applyActiveChannel(chat: ChatComponent) {
        val channel = activeChannel() ?: return
        applyMessages(chat, messageCache.visibleMessages(channel, channels))
    }

    private fun applyMessages(chat: ChatComponent, messages: List<GuiMessage>) {
        val accessor = chat as ChatComponentBridge
        val allMessages = accessor.`lantern$getAllMessages`()
        allMessages.clear()
        allMessages.addAll(messages)
        chat.resetChatScroll()
        accessor.`lantern$refreshTrimmedMessages`()
    }
}
