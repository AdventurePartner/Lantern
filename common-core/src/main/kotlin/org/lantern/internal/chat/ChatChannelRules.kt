package org.lantern.internal.chat

object ChatChannelRules {
    fun normalizePrefixes(raw: Iterable<String>): List<String> {
        return raw.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun normalizeChannels(raw: Iterable<ChatChannel>): List<ChatChannel> {
        val channels = ArrayList<ChatChannel>()
        val seenIds = HashSet<String>()

        raw.forEach { channel ->
            val id = channel.id.trim()
            if (id.isEmpty() || !seenIds.add(id)) return@forEach

            val displayName = channel.displayName.trim().ifEmpty { id }
            channels.add(ChatChannel(id, displayName, normalizePrefixes(channel.prefixes), normalizeFilter(channel.filter)))
        }

        return channels
    }

    fun matches(text: String, channel: ChatChannel, channels: List<ChatChannel>): Boolean {
        return matchesOwnPrefixes(text, channel) && !matchesFilteredChannel(text, channel, channels)
    }

    fun matchesOwnPrefixes(text: String, channel: ChatChannel): Boolean {
        return channel.prefixes.isEmpty() || channel.prefixes.any { text.startsWith(it) }
    }

    private fun normalizeFilter(raw: Iterable<String>): List<String> {
        return raw.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun matchesFilteredChannel(text: String, channel: ChatChannel, channels: List<ChatChannel>): Boolean {
        return channel.filter.any { filteredId ->
            val filteredChannel = channels.firstOrNull { it.id == filteredId } ?: return@any false
            filteredChannel.prefixes.isNotEmpty() && filteredChannel.prefixes.any { text.startsWith(it) }
        }
    }
}
