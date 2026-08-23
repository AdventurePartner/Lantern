package org.lantern.listen

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.persistence.PersistentDataType
import org.lantern.LanternPlugin
import org.lantern.config.WardrobeConfig
import org.lantern.gui.WardrobeHolder
import org.lantern.gui.WardrobeGui
import org.lantern.gui.WardrobeSelectHolder
import org.lantern.handler.CostumeAssignmentHandler
import org.lantern.network.NetworkHandler

/**
 * 衣柜 GUI 点击/拖拽处理。顶部容器为 Wardrobe*Holder 时一律取消事件（纯展示护栏）。
 */
class WardrobeListener : Listener {
    private val costumeKey: NamespacedKey = NamespacedKey(LanternPlugin.instance, "wardrobe_costume")
    private val actionKey: NamespacedKey = NamespacedKey(LanternPlugin.instance, "wardrobe_action")

    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder ?: return
        if (holder !is WardrobeHolder && holder !is WardrobeSelectHolder) return
        val player = e.whoClicked as? Player ?: return
        e.isCancelled = true

        when (holder) {
            is WardrobeHolder -> {
                val ws = WardrobeConfig.slotByIndex[e.rawSlot] ?: return
                WardrobeGui.openSelect(player, ws.name)
            }
            is WardrobeSelectHolder -> {
                val item = e.currentItem ?: return
                val pdc = item.itemMeta?.persistentDataContainer ?: return
                val costumeId = pdc.get(costumeKey, PersistentDataType.STRING)
                val action = pdc.get(actionKey, PersistentDataType.STRING)
                when {
                    costumeId != null -> {
                        CostumeAssignmentHandler.assign(player.uniqueId, holder.slot, costumeId)
                        afterChange(player)
                    }
                    action == "unequip" -> {
                        CostumeAssignmentHandler.remove(player.uniqueId, holder.slot)
                        afterChange(player)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onDrag(e: InventoryDragEvent) {
        val holder = e.inventory.holder ?: return
        if (holder is WardrobeHolder || holder is WardrobeSelectHolder) {
            e.isCancelled = true
        }
    }

    private fun afterChange(player: Player) {
        CostumeAssignmentHandler.save()
        NetworkHandler.broadcastCostumeAssignment()
        WardrobeGui.openMain(player)
    }
}
