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

    val slotIndex: Int = source?.substringAfterLast('_')?.toIntOrNull() ?: -1

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
}
