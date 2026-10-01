package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.platform.InventoryBridge
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.panel.PanelWidgetImpl
import org.lantern.uix.widget.slot.HotbarSelectionWidgetImpl
import org.lantern.uix.widget.slot.SlotWidgetImpl

object HotbarSelectionRenderer : IWidgetRenderer<HotbarSelectionWidgetImpl> {

    private const val DEFAULT_SIZE = 24

    override fun render(
        widget: HotbarSelectionWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val player = Minecraft.getInstance().player ?: return
        val targetIndex = SlotWidgetImpl.HOTBAR_START + InventoryBridge.selectedSlot(player)
        val slot = findSlot(rootOf(widget), targetIndex) ?: return

        // 画布已平移到选中框父容器的原点，把目标 slot 的绝对坐标换算回这个局部坐标系
        val parent = widget.parent as? IWidget
        val originX = parent?.let { WidgetGeometry.absoluteX(it) } ?: 0
        val originY = parent?.let { WidgetGeometry.absoluteY(it) } ?: 0
        val x = WidgetGeometry.absoluteX(slot) - originX + style.getInt(StyleProperty.X)
        val y = WidgetGeometry.absoluteY(slot) - originY + style.getInt(StyleProperty.Y)
        val w = style.getInt(StyleProperty.WIDTH, DEFAULT_SIZE)
        val h = style.getInt(StyleProperty.HEIGHT, DEFAULT_SIZE)
        BackgroundPainter.paint(graphics, style, x, y, w, h)
        ImageRenderer.drawTexture(graphics, widget.texture, x, y, w, h)
    }

    private fun rootOf(widget: IWidget): IWidget {
        var current = widget
        while (true) {
            current = current.parent as? IWidget ?: return current
        }
    }

    private fun findSlot(widget: IWidget, slotIndex: Int): SlotWidgetImpl? {
        if (widget is SlotWidgetImpl && widget.slotIndex == slotIndex) return widget
        if (widget is PanelWidgetImpl) {
            for (child in widget.children) findSlot(child, slotIndex)?.let { return it }
        }
        return null
    }
}
