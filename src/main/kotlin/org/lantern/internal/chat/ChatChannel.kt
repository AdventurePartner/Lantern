package org.lantern.internal.chat

data class ChatChannel(
    val id: String,
    val displayName: String,
    val prefixes: List<String>,
    val filter: List<String> = emptyList()
)
