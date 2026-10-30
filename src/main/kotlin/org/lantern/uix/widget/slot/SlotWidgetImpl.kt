package org.lantern.uix.widget.slot

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import org.lantern.uix.widget.BaseWidget
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.panel.PanelWidgetImpl

class SlotWidgetImpl(
    val source: String? = null
) : BaseWidget() {

    override val widgetType: String = "slot"

    private val target: SlotTarget? = SlotTarget.parse(source)

    /** 生存背包 InventoryMenu 下标（0 合成结果、1-4 合成格、5-8 盔甲、9-35 主背包、36-44 快捷栏、45 副手）。 */
    val slotIndex: Int = target?.inventoryMenuIndex ?: -1

    /** 在 [menu] 里找到 source 指向的槽位。 */
    fun resolve(menu: AbstractContainerMenu): Slot? = target?.resolve(menu)

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }

    companion object {
        const val HOTBAR_START = SlotTarget.HOTBAR_MENU_START

        fun containsSlot(widget: IWidget): Boolean {
            if (widget is SlotWidgetImpl && widget.slotIndex >= 0) return true
            if (widget is PanelWidgetImpl) {
                for (child in widget.children) if (containsSlot(child)) return true
            }
            return false
        }
    }
}
