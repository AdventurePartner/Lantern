package org.lantern.uix.widget.slot

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

/** slot 节点 `source` 指向的格子。 */
sealed interface SlotTarget {

    /** 对应的生存背包 InventoryMenu 下标，用于镜像渲染与快捷栏选中框；没有对应格子时为 -1。 */
    val inventoryMenuIndex: Int

    fun resolve(menu: AbstractContainerMenu): Slot?

    /** `slot_N` / 纯数字：当前菜单的第 N 个槽位，含义随界面变化（箱子界面里是箱子的格子）。 */
    data class MenuIndex(val index: Int) : SlotTarget {
        override val inventoryMenuIndex: Int get() = index

        override fun resolve(menu: AbstractContainerMenu): Slot? = menu.slots.getOrNull(index)
    }

    /**
     * 玩家背包第 N 格（0-8 快捷栏、9-35 主背包、36-39 靴/腿/胸/头、40 副手），
     * 在任何容器界面里都指向同一格。
     */
    data class PlayerInventory(val index: Int) : SlotTarget {
        override val inventoryMenuIndex: Int
            get() = when (index) {
                in 0..8 -> HOTBAR_MENU_START + index
                in 9..35 -> index
                in 36..39 -> 44 - index
                OFFHAND -> OFFHAND_MENU_INDEX
                else -> -1
            }

        override fun resolve(menu: AbstractContainerMenu): Slot? =
            menu.slots.firstOrNull { it.container is Inventory && it.containerSlot == index }
    }

    companion object {
        const val HOTBAR_MENU_START = 36
        const val OFFHAND_MENU_INDEX = 45
        private const val OFFHAND = 40

        private val NAMED = mapOf(
            "offhand" to OFFHAND,
            "armor_feet" to 36,
            "armor_legs" to 37,
            "armor_chest" to 38,
            "armor_head" to 39
        )

        /** `hotbar_0..8`、`inventory_0..35`、`offhand`、`armor_*` 按玩家背包定位，其余取 `*_N` 的 N 作菜单下标。 */
        fun parse(source: String?): SlotTarget? {
            val key = source?.trim()?.lowercase() ?: return null
            NAMED[key]?.let { return PlayerInventory(it) }
            val number = key.substringAfterLast('_').toIntOrNull() ?: return null
            return when {
                key.startsWith("hotbar_") -> if (number in 0..8) PlayerInventory(number) else null
                key.startsWith("inventory_") -> if (number in 0..35) PlayerInventory(number) else null
                number >= 0 -> MenuIndex(number)
                else -> null
            }
        }
    }
}
