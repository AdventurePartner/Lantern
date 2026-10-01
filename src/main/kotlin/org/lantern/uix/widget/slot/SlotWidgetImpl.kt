package org.lantern.uix.widget.slot

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lantern.uix.interaction.SlotInteractionHelper
import org.lantern.uix.widget.BaseWidget

class SlotWidgetImpl(
    val source: String? = null
) : BaseWidget() {

    override val widgetType: String = "slot"

    /** 生存背包 InventoryMenu 下标（0 合成结果、1-4 合成格、5-8 盔甲、9-35 主背包、36-44 快捷栏、45 副手）。 */
    val slotIndex: Int = parseSlotIndex(source)

    init {
        if (slotIndex >= 0) {
            onClick { event ->
                // 容器界面上 slot 节点是位置声明：原版槽位已被移到该处，
                // 点击/拖拽全部交由原版处理（不消费，事件透传）。
                // 仅 HUD 等非容器场景保留镜像点击转发。
                if (Minecraft.getInstance().screen !is AbstractContainerScreen<*>) {
                    SlotInteractionHelper.handleSlotClick(slotIndex, event.button)
                    event.consume()
                }
            }
        }
    }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }

    companion object {
        const val HOTBAR_START = 36
        const val OFFHAND = 45

        /** `hotbar_0..8` → 36..44，`offhand` → 45，其余取 `*_N` 的 N（兼容 `slot_36` 写法）。 */
        fun parseSlotIndex(source: String?): Int {
            val key = source?.trim()?.lowercase() ?: return -1
            if (key == "offhand") return OFFHAND
            val number = key.substringAfterLast('_').toIntOrNull() ?: return -1
            if (key.startsWith("hotbar_")) {
                return if (number in 0..8) HOTBAR_START + number else -1
            }
            return number
        }
    }
}
