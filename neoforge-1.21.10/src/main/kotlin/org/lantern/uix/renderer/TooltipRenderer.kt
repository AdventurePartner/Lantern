package org.lantern.uix.renderer

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.interaction.InventorySlotInteraction
import org.lantern.uix.util.TextLines
import org.lantern.uix.widget.IWidget

object TooltipRenderer {
    /** GuiCanvas 在自己的 render 里调用，登记的延迟提示由 renderWithTooltipAndSubtitles 末尾画出。 */
    fun renderTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        val item = InventorySlotInteraction.hoveredItem(root)
        if (item != null) {
            graphics.setTooltipForNextFrame(font, item, mouseX, mouseY)
            return
        }
        val tooltip = findTooltip(root, mouseX, mouseY) ?: return
        graphics.setComponentTooltipForNextFrame(font, TextLines.components(tooltip), mouseX, mouseY)
    }

    /**
     * 贴在原版界面上的 overlay 由 ScreenEvent.Render.Post 触发：原版已在 renderWithTooltipAndSubtitles 末尾
     * 画完延迟提示，GuiGraphics 又是每帧新建的，这时 setTooltipForNextFrame 登记的提示永远不会画，只能立即绘制。
     * overlay 里没有可交互的 slot（hoveredItem 只认 GuiCanvas），不用管物品提示。
     */
    fun renderOverlayTooltipPass(root: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val tooltip = findTooltip(root, mouseX, mouseY) ?: return
        val font = Minecraft.getInstance().font
        val lines = TextLines.components(tooltip).map { ClientTooltipComponent.create(it.visualOrderText) }
        graphics.nextStratum()
        graphics.renderTooltip(font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null)
    }

    /** 鼠标下最深、最上层写了 tooltip 的可见组件；命中规则同点击（裁切、不可见容器、未参与布局的默认尺寸）。 */
    private fun findTooltip(root: IWidget, mouseX: Int, mouseY: Int): String? =
        (EventDispatcher.findDeepest(root, mouseX, mouseY) { it is IWidget && it.tooltip.isNotEmpty() } as? IWidget)
            ?.tooltip
}
