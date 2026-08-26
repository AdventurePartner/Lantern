package org.lantern.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.lantern.LanternPlugin
import org.lantern.cache.BlockModelCache
import org.lantern.cache.CostumeCache
import org.lantern.cache.ItemIconCache
import org.lantern.cache.KeyCache
import org.lantern.handler.CacheHandler

object Configurations {
    val plugin: LanternPlugin by lazy { LanternPlugin.instance }

    lateinit var characters: FileConfiguration
    lateinit var models: FileConfiguration
    lateinit var costumes: FileConfiguration

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        LanternPlugin.instance.saveResource("characters.yml", "characters.yml", false) {
            characters = YamlConfiguration.loadConfiguration(it)
        }
        LanternPlugin.instance.saveResource("entityModels.yml", "entityModels.yml", false) {
            models = YamlConfiguration.loadConfiguration(it)
        }
        LanternPlugin.instance.saveResource("keys.yml", "keys.yml", false) {
            CacheHandler.keys.clear()
            val data = YamlConfiguration.loadConfiguration(it)
            data.getKeys(false).forEach { key ->
                val section = data.getConfigurationSection(key) ?: return@forEach
                CacheHandler.keys[key.lowercase()] = KeyCache(section)
            }
        }
        // 相机配置依赖 keys.yml 先加载（键位冲突检测）
        LanternPlugin.instance.saveResource("camera.yml", "camera.yml", false) {
            org.lantern.camera.ShoulderCameraService.load(YamlConfiguration.loadConfiguration(it))
        }
        LanternPlugin.instance.saveResource("itemIcons.yml", "itemIcons.yml", false) {
            CacheHandler.itemIcons.clear()
            val data = YamlConfiguration.loadConfiguration(it)
            data.getKeys(false).forEach { i ->
                val section = data.getConfigurationSection(i) ?: return@forEach
                CacheHandler.itemIcons[i.toInt()] = ItemIconCache(section)
            }
        }
        LanternPlugin.instance.saveResource("costumes.yml", "costumes.yml", false) {
            CacheHandler.costumes.clear()
            val data = YamlConfiguration.loadConfiguration(it)
            data.getKeys(false).forEach { key ->
                val section = data.getConfigurationSection(key) ?: return@forEach
                CacheHandler.costumes[key] = CostumeCache(section)
            }
        }
        LanternPlugin.instance.saveResource("blockModels.yml", "blockModels.yml", false) {
            CacheHandler.blockModels.clear()
            val data = YamlConfiguration.loadConfiguration(it)
            data.getKeys(false).forEach { key ->
                val section = data.getConfigurationSection(key) ?: return@forEach
                CacheHandler.blockModels[key] = BlockModelCache(section)
            }
            CacheHandler.rebuildBlockModelIndices()
        }
        UiConfigurations.load()
        WardrobeConfig.load()
    }
}