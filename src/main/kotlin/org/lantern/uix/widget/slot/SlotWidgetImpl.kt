package org.lantern.uix.widget.slot

import net.minecraft.client.gui.GuiGraphics
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
                SlotInteractionHelper.handleSlotClick(slotIndex, event.button)
                event.consume()
            }
        }
    }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
