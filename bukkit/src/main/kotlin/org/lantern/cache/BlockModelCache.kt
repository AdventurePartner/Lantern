package org.lantern.cache

import org.bukkit.configuration.ConfigurationSection

class BlockModelCache(section: ConfigurationSection) {
    val customVariation: Int = section.getInt("custom_variation")
    val customModelData: Int = section.getInt("custom_model_data", -1)
    val displayName: String = section.getString("display-name") ?: ""

    // GeckoLib 模型资源路径
    val geo: String = section.getString("geo") ?: ""
    val texture: String = section.getString("texture") ?: ""
    val animation: String = section.getString("animation") ?: ""
    val scale: Float = section.getDouble("scale", 1.0).toFloat()
    val idleAnimation: String = section.getString("idle-animation") ?: "idle"
    val blockScale: Float = section.getDouble("block-scale", 1.0).toFloat()
    val itemOffsetX: Float = section.getDouble("item-offset-x", 0.0).toFloat()
    val itemOffsetY: Float = section.getDouble("item-offset-y", 0.0).toFloat()
    val itemOffsetZ: Float = section.getDouble("item-offset-z", 0.0).toFloat()
    val hardness: Float = section.getDouble("hardness", 1.5).toFloat()
    val preferredTool: String = section.getString("tool-type") ?: ""
    val breakSound: String = section.getString("break-sound") ?: ""
}
