package org.lantern.cache

import org.bukkit.configuration.ConfigurationSection

class ItemIconCache(section: ConfigurationSection) {
    val identifier: String = section.getString("identifier") ?: ""
    val texture: String = section.getString("texture") ?: ""
    val type: String = section.getString("type") ?: "generated"
}
