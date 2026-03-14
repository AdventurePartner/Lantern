package org.lantern.cache

import org.bukkit.configuration.ConfigurationSection

class CostumeCache(section: ConfigurationSection) {
    val displayName: String = section.getString("display-name") ?: ""
    val geo: String = section.getString("geo") ?: ""
    val texture: String = section.getString("texture") ?: ""
    val animationFile: String = section.getString("animations.file") ?: ""
    val animationStates: Map<String, String> = run {
        val states = mutableMapOf<String, String>()
        val statesSection = section.getConfigurationSection("animations.states")
        statesSection?.getKeys(false)?.forEach { key ->
            statesSection.getString(key)?.let { states[key] = it }
        }
        states
    }
    val scale: Double = section.getDouble("scale", 1.0)
    val offsetX: Double = section.getDouble("offset.x", 0.0)
    val offsetY: Double = section.getDouble("offset.y", 0.0)
    val offsetZ: Double = section.getDouble("offset.z", 0.0)
}
