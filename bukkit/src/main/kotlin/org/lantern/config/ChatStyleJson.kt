package org.lantern.config

import com.google.gson.JsonObject
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.lantern.util.TextUtil.colorify

/**
 * config.yml 的 `chat-style` 段与频道条目的 `style` 段 -> opcode 14 里的 JSON。
 * 这里只做结构转换，键名与取值由客户端校验；颜色值是 `#RRGGBB` 写法，不能走 colorify。
 */
object ChatStyleJson {

    fun read(config: FileConfiguration): JsonObject? {
        val section = config.getConfigurationSection("chat-style") ?: return null
        val json = sectionToJson(section)
        json.getAsJsonObject("notification")?.let { notification ->
            notification.get("text")?.takeIf { it.isJsonPrimitive }?.asString?.let {
                notification.addProperty("text", it.colorify())
            }
        }
        return json
    }

    fun channelStyle(raw: Any?): JsonObject? = when (raw) {
        is ConfigurationSection -> sectionToJson(raw)
        is Map<*, *> -> mapToJson(raw)
        else -> null
    }

    private fun sectionToJson(section: ConfigurationSection): JsonObject =
        mapToJson(section.getValues(false))

    private fun mapToJson(map: Map<*, *>): JsonObject {
        val json = JsonObject()
        map.forEach { (rawKey, value) ->
            val key = rawKey?.toString() ?: return@forEach
            when (value) {
                null -> Unit
                is ConfigurationSection -> json.add(key, sectionToJson(value))
                is Map<*, *> -> json.add(key, mapToJson(value))
                is Number -> json.addProperty(key, value)
                is Boolean -> json.addProperty(key, value)
                else -> json.addProperty(key, value.toString())
            }
        }
        return json
    }
}
