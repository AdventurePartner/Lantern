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
    val slot: String = section.getString("slot") ?: "full_body"
    val boneSync: Boolean = section.getBoolean("bone-sync", true)
    val boneMappingHead: String = section.getString("bone-mapping.head") ?: "head"
    val boneMappingBody: String = section.getString("bone-mapping.body") ?: "body"
    val boneMappingLeftArm: String = section.getString("bone-mapping.left_arm") ?: "left_arm"
    val boneMappingRightArm: String = section.getString("bone-mapping.right_arm") ?: "right_arm"
    val boneMappingLeftLeg: String = section.getString("bone-mapping.left_leg") ?: "left_leg"
    val boneMappingRightLeg: String = section.getString("bone-mapping.right_leg") ?: "right_leg"
}
