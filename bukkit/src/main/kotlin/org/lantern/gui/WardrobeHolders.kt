package org.lantern.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * 衣柜主界面身份标记。持有实际 Inventory 引用以满足 @NotNull getInventory 契约。
 */
class WardrobeHolder : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

/**
 * 衣柜槽位选择界面身份标记，携带当前正在选择的 slot 名称。
 */
class WardrobeSelectHolder(val slot: String) : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}
