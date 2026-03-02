package org.lantern.cache

import org.bukkit.configuration.ConfigurationSection

class KeyCache(section: ConfigurationSection) {
    val press = section.getBoolean("press")
    val commands = section.getStringList("commands")
}