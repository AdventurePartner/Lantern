package org.lantern.cache

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * worldImages.yml 的解析产物：模板表（下发 opcode 22 的 images 段）、
 * 服主自定义动画（yml 原样转 JSON 的 animations 段）与伤害数值配置。
 */
class WorldImageCache(data: YamlConfiguration) {

    /** 模板名 -> 模板字段（与协议 spawn 载荷同构，客户端逐字段解析）。 */
    val images: Map<String, JsonObject>

    /** 动画名 -> 动画定义（duration/loop/tracks 结构原样透传）。 */
    val animations: JsonObject

    val damage: Damage = Damage(data.getConfigurationSection("damage"))

    init {
        val imageMap = LinkedHashMap<String, JsonObject>()
        data.getConfigurationSection("images")?.getKeys(false)?.forEach { name ->
            val section = data.getConfigurationSection("images")?.getConfigurationSection(name) ?: return@forEach
            imageMap[name] = sectionToJson(section)
        }
        images = imageMap

        val animObj = JsonObject()
        data.getConfigurationSection("animations")?.getKeys(false)?.forEach { name ->
            val section = data.getConfigurationSection("animations")?.getConfigurationSection(name) ?: return@forEach
            animObj.add(name, sectionToJson(section))
        }
        animations = animObj
    }

    /** 伤害数值弹出的参数（默认值与 worldImages.yml 出厂模板一致，模板节被删也能工作）。 */
    class Damage(section: ConfigurationSection?) {
        val enabled: Boolean = section?.getBoolean("enabled", true) ?: true
        val format: String = section?.getString("format", "%.0f") ?: "%.0f"
        val color: String = section?.getString("color", "#FF5555") ?: "#FF5555"
        val minDamage: Double = section?.getDouble("min-damage", 0.5) ?: 0.5
        val mergeMs: Long = section?.getLong("merge-ms", 250L) ?: 250L
        val radius: Double = section?.getDouble("radius", 48.0) ?: 48.0
        val firstPerson: Boolean = section?.getBoolean("first-person", false) ?: false
        val scale: Double = section?.getDouble("scale", 1.0) ?: 1.0
        val size: Double = section?.getDouble("size", 0.4) ?: 0.4
        val animation: String = section?.getString("animation", "damage_pop") ?: "damage_pop"
    }

    companion object {
        /** yml 配置节 -> JsonObject：递归处理子节、列表（动画关键帧是 map 列表）与标量。 */
        private fun sectionToJson(section: ConfigurationSection): JsonObject {
            val obj = JsonObject()
            section.getKeys(false).forEach { key ->
                anyToJson(section.get(key))?.let { obj.add(key, it) }
            }
            return obj
        }

        private fun anyToJson(value: Any?): JsonElement? = when (value) {
            is ConfigurationSection -> sectionToJson(value)
            is Map<*, *> -> JsonObject().apply {
                value.forEach { (k, v) -> anyToJson(v)?.let { add(k.toString(), it) } }
            }
            is List<*> -> com.google.gson.JsonArray().apply {
                value.forEach { v -> anyToJson(v)?.let(::add) }
            }
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Char -> JsonPrimitive(value)
            else -> null
        }
    }
}
