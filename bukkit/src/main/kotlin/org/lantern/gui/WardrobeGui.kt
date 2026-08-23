package org.lantern.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.lantern.LanternPlugin
import org.lantern.config.WardrobeConfig
import org.lantern.handler.CacheHandler
import org.lantern.handler.CostumeAssignmentHandler
import org.lantern.util.TextUtil.colorify

/**
 * 衣柜 GUI 构建器。每次构建新的 Bukkit Inventory，纯展示（所有点击由 WardrobeListener 取消）。
 */
object WardrobeGui {
    private val costumeKey: NamespacedKey = NamespacedKey(LanternPlugin.instance, "wardrobe_costume")
    private val actionKey: NamespacedKey = NamespacedKey(LanternPlugin.instance, "wardrobe_action")

    fun openMain(player: Player) {
        player.openInventory(buildMain(player))
    }

    fun openSelect(player: Player, slot: String) {
        player.openInventory(buildSelect(slot))
    }

    private fun buildMain(player: Player): org.bukkit.inventory.Inventory {
        val holder = WardrobeHolder()
        val inv = Bukkit.createInventory(holder, WardrobeConfig.size, WardrobeConfig.title)
        holder.inv = inv
        for (i in WardrobeConfig.borderIndices) {
            inv.setItem(i, WardrobeConfig.borderItem)
        }
        val assigned = CostumeAssignmentHandler.get(player.uniqueId).orEmpty()
        for (ws in WardrobeConfig.slots) {
            val costumeId = assigned[ws.name]
            val item = if (costumeId != null) {
                val cache = CacheHandler.costumes[costumeId]
                buildDisplayItem(Material.PAPER, cache?.displayName?.takeIf { it.isNotEmpty() } ?: costumeId)
            } else {
                WardrobeConfig.emptySlotItem
            }
            inv.setItem(ws.index, item)
        }
        return inv
    }

    private fun buildSelect(slot: String): org.bukkit.inventory.Inventory {
        val holder = WardrobeSelectHolder(slot)
        val inv = Bukkit.createInventory(holder, 54, "选择: $slot".colorify())
        holder.inv = inv
        var index = 0
        for ((id, cache) in CacheHandler.costumes) {
            if (cache.slot != slot) continue
            val item = buildDisplayItem(Material.PAPER, cache.displayName.takeIf { it.isNotEmpty() } ?: id)
            val meta = item.itemMeta ?: continue
            meta.persistentDataContainer.set(costumeKey, PersistentDataType.STRING, id)
            item.itemMeta = meta
            inv.setItem(index++, item)
        }
        val unequip = buildDisplayItem(Material.BARRIER, "&c卸下".colorify())
        val unequipMeta = unequip.itemMeta
        if (unequipMeta != null) {
            unequipMeta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, "unequip")
            unequip.itemMeta = unequipMeta
            inv.setItem(53, unequip)
        }
        return inv
    }

    private fun buildDisplayItem(material: Material, name: String): ItemStack {
        return ItemStack(material).apply {
            val meta = itemMeta ?: return@apply
            meta.setDisplayName(name)
            itemMeta = meta
        }
    }
}
