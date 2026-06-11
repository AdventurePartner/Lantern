package org.lantern.internal.chat

class ChatChannelMessageCache<T>(
    private val maxMessages: Int,
    private val textOf: (T) -> String
) {
    private val messages = ArrayDeque<T>()

    fun add(message: T) {
        messages.addFirst(message)
        trimToLimit()
    }

    fun seedIfEmpty(messages: List<T>) {
        if (this.messages.isNotEmpty()) {
            return
        }

        this.messages.addAll(messages.take(maxMessages))
    }

    fun clear() {
        messages.clear()
    }

    fun allMessages(): List<T> {
        return messages.toList()
    }

    fun visibleMessages(channel: ChatChannel, channels: List<ChatChannel>): List<T> {
        return messages.filter { ChatChannelRules.matches(textOf(it), channel, channels) }
    }

    fun isEmpty(): Boolean {
        return messages.isEmpty()
    }

    private fun trimToLimit() {
        while (messages.size > maxMessages) {
            messages.removeLast()
        }
    }
}
