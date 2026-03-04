package org.lantern.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.configuration.file.YamlConfiguration
import org.lantern.LanternPlugin
import java.io.File

object UiConfigurations {

    private val screens = mutableListOf<JsonObject>()

    fun getScreens(): List<JsonObject> = screens

    fun load() {
        screens.clear()
        loadDir("huds", defaultResources = listOf("huds/example.yml"))
        loadDir("screens", defaultResources = listOf("screens/example.yml"))
    }

    private fun loadDir(dirName: String, defaultResources: List<String>) {
        val dir = File(LanternPlugin.instance.dataFolder, dirName)
        if (!dir.exists()) {
            dir.mkdirs()
            defaultResources.forEach { runCatching { LanternPlugin.instance.saveResource(it, false) } }
        }
        dir.listFiles { f -> f.extension == "yml" }?.forEach { file ->
            runCatching { parseScreen(YamlConfiguration.loadConfiguration(file)) }
                .onSuccess { screens.add(it) }
                .onFailure { LanternPlugin.instance.logger.warning("Failed to load UI ${file.name}: ${it.message}") }
        }
    }

    private fun parseScreen(config: org.bukkit.configuration.ConfigurationSection): JsonObject {
        val screen = JsonObject()
        screen.addProperty("id", config.getString("id") ?: "unknown")
        screen.addProperty("screen-type", config.getString("screen-type") ?: "hud")

        // parse named styles
        val stylesObj = JsonObject()
        config.getConfigurationSection("styles")?.getKeys(false)?.forEach { name ->
            val ruleSection = config.getConfigurationSection("styles.$name") ?: return@forEach
            stylesObj.add(name, parseStyleSection(ruleSection))
        }
        screen.add("styles", stylesObj)

        // parse root component tree
        config.getConfigurationSection("root")?.let { screen.add("root", parseNode(it)) }
        return screen
    }

    private fun parseNode(section: org.bukkit.configuration.ConfigurationSection): JsonObject {
        val node = JsonObject()
        node.addProperty("type", section.getString("type") ?: "panel")

        section.getString("id")?.let { node.addProperty("id", it) }
        section.getString("text")?.let { node.addProperty("text", it) }
        section.getString("action")?.let { node.addProperty("action", it) }
        section.getString("texture")?.let { node.addProperty("texture", it) }
        section.getString("style-ref")?.let { node.addProperty("style-ref", it) }
        section.getString("value")?.let { node.addProperty("value", it) }
        section.getString("placeholder")?.let { node.addProperty("placeholder", it) }
        section.getString("on-change")?.let { node.addProperty("on-change", it) }
        if (section.contains("max-length")) node.addProperty("max-length", section.getInt("max-length"))

        section.getConfigurationSection("style")?.let { node.add("style", parseStyleSection(it)) }

        val childrenList = section.getList("children")
        if (!childrenList.isNullOrEmpty()) {
            val childrenArray = JsonArray()
            childrenList.filterIsInstance<Map<*, *>>().forEach { map ->
                childrenArray.add(parseNodeFromMap(map))
            }
            node.add("children", childrenArray)
        }
        return node
    }

    /** Parse a node directly from a raw YAML map (used for list-based children). */
    private fun parseNodeFromMap(map: Map<*, *>): JsonObject {
        val node = JsonObject()
        node.addProperty("type", map["type"]?.toString() ?: "panel")

        map["id"]?.toString()?.let { node.addProperty("id", it) }
        map["text"]?.toString()?.let { node.addProperty("text", it) }
        map["action"]?.toString()?.let { node.addProperty("action", it) }
        map["texture"]?.toString()?.let { node.addProperty("texture", it) }
        map["style-ref"]?.toString()?.let { node.addProperty("style-ref", it) }
        map["value"]?.toString()?.let { node.addProperty("value", it) }
        map["placeholder"]?.toString()?.let { node.addProperty("placeholder", it) }
        map["on-change"]?.toString()?.let { node.addProperty("on-change", it) }
        (map["max-length"] as? Int)?.let { node.addProperty("max-length", it) }

        @Suppress("UNCHECKED_CAST")
        (map["style"] as? Map<*, *>)?.let { node.add("style", parseStyleFromMap(it)) }

        @Suppress("UNCHECKED_CAST")
        (map["children"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.takeIf { it.isNotEmpty() }?.let { children ->
            val childrenArray = JsonArray()
            children.forEach { childMap -> childrenArray.add(parseNodeFromMap(childMap)) }
            node.add("children", childrenArray)
        }

        return node
    }

    private fun parseStyleSection(section: org.bukkit.configuration.ConfigurationSection): JsonObject {
        val style = JsonObject()
        section.getKeys(false).forEach { key ->
            when (val v = section.get(key)) {
                is Int -> style.addProperty(key, v)
                is Double -> style.addProperty(key, v)
                is Float -> style.addProperty(key, v)
                is Boolean -> style.addProperty(key, v)
                else -> style.addProperty(key, v?.toString() ?: "")
            }
        }
        return style
    }

    private fun parseStyleFromMap(map: Map<*, *>): JsonObject {
        val style = JsonObject()
        map.forEach { (k, v) ->
            val key = k?.toString() ?: return@forEach
            when (v) {
                is Int -> style.addProperty(key, v)
                is Long -> style.addProperty(key, v)
                is Double -> style.addProperty(key, v)
                is Float -> style.addProperty(key, v)
                is Boolean -> style.addProperty(key, v)
                else -> style.addProperty(key, v?.toString() ?: "")
            }
        }
        return style
    }
}
