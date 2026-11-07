package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.interaction.InventorySlotInteraction
import org.lantern.uix.widget.IWidget
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.util.TextLines

object TooltipRenderer {

    fun renderTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        val item = InventorySlotInteraction.hoveredItem(root)
        if (item != null) {
            graphics.renderTooltip(font, item, mouseX, mouseY)
            return
        }
        val tooltip = findTooltip(root, mouseX, mouseY) ?: return
        graphics.renderComponentTooltip(font, TextLines.components(tooltip), mouseX, mouseY)
    }

    /**
     * 贴在原版界面上的 overlay，在原版界面画完之后调用。1.20.1 / 1.21.1 的 renderTooltip 本来就立即绘制，
     * 与 GuiCanvas 同一套；1.21.10 / 1.21.11 副本的延迟提示此时已画不出来，那边改为立即绘制。
     */
    fun renderOverlayTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) =
        renderTooltipPass(root, graphics, mouseX, mouseY)

    /** 鼠标下最深、最上层写了 tooltip 的可见组件；命中规则同点击（裁切、不可见容器、未参与布局的默认尺寸）。 */
    private fun findTooltip(root: IWidget, mouseX: Int, mouseY: Int): String? =
        (EventDispatcher.findDeepest(root, mouseX, mouseY) { it is IWidget && it.tooltip.isNotEmpty() } as? IWidget)
            ?.tooltip
}
