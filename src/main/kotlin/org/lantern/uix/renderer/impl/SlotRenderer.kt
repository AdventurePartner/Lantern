package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.slot.SlotWidgetImpl

object SlotRenderer : IWidgetRenderer<SlotWidgetImpl> {

    private const val DEFAULT_SIZE = 18

    override fun render(
        widget: SlotWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, DEFAULT_SIZE)
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, DEFAULT_SIZE)

        // Slot background border (MC style)
        graphics.fill(x, y, x + w, y + h, 0xFF8B8B8B.toInt())
        graphics.fill(x, y, x + w - 1, y + 1, 0xFF373737.toInt())
        graphics.fill(x, y, x + 1, y + h - 1, 0xFF373737.toInt())
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B.toInt())
        graphics.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF.toInt())
        graphics.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF.toInt())
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF555555.toInt())

        // Render item from container slot
        if (widget.slotIndex >= 0) {
            val screen = Minecraft.getInstance().screen
            if (screen is AbstractContainerScreen<*>) {
                val slots = screen.menu.slots
                if (widget.slotIndex < slots.size) {
                    val itemStack = slots[widget.slotIndex].item
                    if (!itemStack.isEmpty) {
                        graphics.renderItem(itemStack, x + 1, y + 1)
                        graphics.renderItemDecorations(Minecraft.getInstance().font, itemStack, x + 1, y + 1)
                    }
                }
            }
        }

        // Hover highlight
        if (widget.hovered) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x80FFFFFF.toInt())
        }
    }
}
