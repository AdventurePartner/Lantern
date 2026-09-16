package org.lantern.cache

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.configuration.ConfigurationSection

class CostumeCache(section: ConfigurationSection) {
    val displayName: String = section.getString("display-name") ?: ""
    val geo: String = section.getString("geo") ?: ""
    val texture: String = section.getString("texture") ?: ""
    val animationFile: String = section.getString("animations.file") ?: ""

    /**
     * 状态表（双形态，客户端 AnimationStateMapping.fromStatesJson 对应解析）：
     * string = 纯动画名（LOOP/5tick，旧格式）；
     * object = {animation, mode: loop|once|hold, transition}——玩家动作的
     * 一次性（jump）与持有（sneak）语义需要 mode，随 P1 玩家宿主化引入
     */
    val animationStates: Map<String, JsonElement> = run {
        val states = LinkedHashMap<String, JsonElement>()
        val statesSection = section.getConfigurationSection("animations.states")
        statesSection?.getKeys(false)?.forEach { key ->
            when (val value = statesSection.get(key)) {
                is String -> states[key] = JsonPrimitive(value)
                is ConfigurationSection -> {
                    val animation = value.getString("animation") ?: return@forEach
                    val obj = JsonObject()
                    obj.addProperty("animation", animation)
                    value.getString("mode")?.let { obj.addProperty("mode", it) }
                    if (value.isInt("transition")) obj.addProperty("transition", value.getInt("transition"))
                    states[key] = obj
                }
            }
        }
        states
    }
    val scale: Double = section.getDouble("scale", 1.0)
    val offsetX: Double = section.getDouble("offset.x", 0.0)
    val offsetY: Double = section.getDouble("offset.y", 0.0)
    val offsetZ: Double = section.getDouble("offset.z", 0.0)
    val slot: String = section.getString("slot") ?: "full_body"
    val boneSync: Boolean = section.getBoolean("bone-sync", true)

    /** P1 玩家宿主化：客户端由 AnimationHost 四层层栈驱动（决策 A 整替路线） */
    val hostDriven: Boolean = section.getBoolean("host-driven", false)
    val boneMappingHead: String = section.getString("bone-mapping.head") ?: "head"
    val boneMappingBody: String = section.getString("bone-mapping.body") ?: "body"
    val boneMappingLeftArm: String = section.getString("bone-mapping.left_arm") ?: "left_arm"
    val boneMappingRightArm: String = section.getString("bone-mapping.right_arm") ?: "right_arm"
    val boneMappingLeftLeg: String = section.getString("bone-mapping.left_leg") ?: "left_leg"
    val boneMappingRightLeg: String = section.getString("bone-mapping.right_leg") ?: "right_leg"
}
