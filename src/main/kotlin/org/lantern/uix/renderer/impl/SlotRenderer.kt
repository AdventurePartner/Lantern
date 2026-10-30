package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import org.lantern.uix.interaction.InventorySlotInteraction
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.renderer.UixRenderContext
import org.lantern.uix.style.Background
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.slot.SlotWidgetImpl

object SlotRenderer : IWidgetRenderer<SlotWidgetImpl> {

    private const val DEFAULT_SIZE = 18
    private const val ITEM_SIZE = 16
    private const val DRAG_HIGHLIGHT = 0x80FFFFFF.toInt()
    private val DEFAULT_BACKGROUND = Background.Custom(::renderDefaultFrame)

    override fun render(
        widget: SlotWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val client = Minecraft.getInstance()
        // 容器 overlay 里 slot 节点是位置声明（原版槽位自身渲染），不画镜像；
        // HUD 与非容器界面按镜像渲染玩家背包
        if (!UixRenderContext.hud && client.screen is AbstractContainerScreen<*>) return
        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        val w = rect?.width ?: style.getInt(StyleProperty.WIDTH, DEFAULT_SIZE)
        val h = rect?.height ?: style.getInt(StyleProperty.HEIGHT, DEFAULT_SIZE)

        BackgroundPainter.paint(graphics, style, x, y, w, h, DEFAULT_BACKGROUND)
        renderMirroredItem(widget, graphics, x, y, w, h)

        if (widget.hovered) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x80FFFFFF.toInt())
        }
    }

    private fun renderDefaultFrame(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, 0xFF8B8B8B.toInt())
        graphics.fill(x, y, x + w - 1, y + 1, 0xFF373737.toInt())
        graphics.fill(x, y, x + 1, y + h - 1, 0xFF373737.toInt())
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B.toInt())
        graphics.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF.toInt())
        graphics.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF.toInt())
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF555555.toInt())
    }

    /**
     * 读取玩家生存背包菜单（客户端常驻）里对应槽位的实时物品，居中画在格子里；
     * gui 界面拖拽分配经过的格子改画预计放下后的物品。
     */
    private fun renderMirroredItem(widget: SlotWidgetImpl, graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val itemX = x + (w - ITEM_SIZE) / 2
        val itemY = y + (h - ITEM_SIZE) / 2
        val preview = InventorySlotInteraction.dragPreview(widget)
        val stack: ItemStack
        val countText: String?
        if (preview != null) {
            stack = preview.first
            countText = preview.second
            graphics.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, DRAG_HIGHLIGHT)
        } else {
            val slots = player.inventoryMenu.slots
            if (widget.slotIndex !in slots.indices) return
            stack = slots[widget.slotIndex].item
            countText = null
        }
        if (stack.isEmpty) return
        // 带实体与种子的重载：指南针/时钟等依赖持有者的物品模型才会正确转动
        graphics.renderItem(player, stack, itemX, itemY, widget.slotIndex)
        graphics.renderItemDecorations(client.font, stack, itemX, itemY, countText)
    }
}
