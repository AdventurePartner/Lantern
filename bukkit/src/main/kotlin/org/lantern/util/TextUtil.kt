package org.lantern.util

import net.md_5.bungee.api.ChatColor

object TextUtil {
    private val colorRegex = Regex("#[A-Fa-f0-9]{6}")

    fun String.colorify(): String {
        val processed = colorRegex.replace(this) { matchResult ->
            ChatColor.of(matchResult.value).toString()
        }
        return ChatColor.translateAlternateColorCodes('&', processed)
    }

}