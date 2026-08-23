package org.lantern.config

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.lantern.LanternPlugin
import org.lantern.util.TextUtil.colorify
import java.io.File

data class WardrobeSlot(val name: String, val index: Int)

/**
 * 衣柜 GUI 配置。读取 gui/wardrobe.yml，解析槽位布局、边框与空槽占位物。
 * reload 时 load() 重入会覆盖所有 lateinit 字段。
 */
object WardrobeConfig {
    lateinit var title: String
    var size: Int = 54
    lateinit var slots: List<WardrobeSlot>
    lateinit var slotByIndex: Map<Int, WardrobeSlot>
    lateinit var slotByName: Map<String, WardrobeSlot>
    lateinit var emptySlotItem: ItemStack
    lateinit var borderItem: ItemStack
    lateinit var borderIndices: Collection<Int>

    private val emptyKey: NamespacedKey = NamespacedKey(LanternPlugin.instance, "wardrobe_empty")

    fun load() {
        val file = File(LanternPlugin.instance.dataFolder, "gui/wardrobe.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            LanternPlugin.instance.saveResource("gui/wardrobe.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(file)

        title = (config.getString("title") ?: "&8衣柜").colorify()
        val rawSize = config.getInt("size", 54)
        require(rawSize in 9..54 && rawSize % 9 == 0) {
            "wardrobe.yml size must be 9..54 and multiple of 9, got $rawSize"
        }
        size = rawSize

        val slotList = mutableListOf<WardrobeSlot>()
        val byIndex = mutableMapOf<Int, WardrobeSlot>()
        val byName = mutableMapOf<String, WardrobeSlot>()
        for (raw in config.getMapList("slots")) {
            @Suppress("UNCHECKED_CAST")
            val entry = raw as Map<String, Any>
            val name = entry["name"] as? String ?: continue
            val index = (entry["index"] as? Number)?.toInt() ?: continue
            require(index in 0 until size) { "wardrobe.yml slot '$name' index $index out of range [0,$size)" }
            require(index !in byIndex) { "wardrobe.yml duplicate slot index $index" }
            val ws = WardrobeSlot(name, index)
            slotList.add(ws)
            byIndex[index] = ws
            byName[name] = ws
        }
        slots = slotList
        slotByIndex = byIndex
        slotByName = byName

        emptySlotItem = buildItem(config.getConfigurationSection("empty-slot"), "GRAY_STAINED_GLASS_PANE", "&7空槽位")
            .also { markEmpty(it) }
        borderItem = buildItem(config.getConfigurationSection("border"), "BLACK_STAINED_GLASS_PANE", " ")
        borderIndices = parseSlots(config.getConfigurationSection("border")?.getString("slots", "0-8,45-53") ?: "0-8,45-53")
    }

    private fun buildItem(section: ConfigurationSection?, defaultMaterial: String, defaultName: String): ItemStack {
        val material = Material.valueOf((section?.getString("material", defaultMaterial) ?: defaultMaterial).uppercase())
        val name = (section?.getString("name", defaultName) ?: defaultName).colorify()
        return ItemStack(material).apply {
            val meta = itemMeta ?: return@apply
            meta.setDisplayName(name)
            itemMeta = meta
        }
    }

    private fun markEmpty(item: ItemStack) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(emptyKey, PersistentDataType.STRING, "true")
        item.itemMeta = meta
    }

    private fun parseSlots(expr: String): List<Int> {
        val result = mutableListOf<Int>()
        for (part in expr.split(",")) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if ('-' in trimmed) {
                val bounds = trimmed.split("-")
                result.addAll((bounds[0].trim().toInt())..(bounds[1].trim().toInt()))
            } else {
                result.add(trimmed.toInt())
            }
        }
        return result
    }
}
