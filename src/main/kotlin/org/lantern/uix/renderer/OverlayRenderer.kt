package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lantern.internal.storage.OverlayMatch
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.canvas.impl.GuiCanvas
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.layout.LayoutCache

object OverlayRenderer {

    /**
     * 顶层通道：渲染 index >= 0 的 overlay（物品/tooltip 之上）。
     * 由 ScreenRenderMixin（renderWithTooltip TAIL）/ NeoForge ScreenEvent.Render.Post 触发。
     */
    fun tryRenderOverlay(screen: Screen, graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (screen is GuiCanvas) return
        val overlays = UiScreenStorage.getOverlaysFor(screen)

        // 容器锚定 overlay 的布局原点平移到容器面板左上角；命中与渲染仍使用屏幕绝对坐标
        val panel = (screen as? AbstractContainerScreen<*>) as? ContainerPanelBridge
        for (match in overlays) {
            if (match.index < 0) continue // 背景层走 renderContainerBackground（物品之下）
            layoutMatch(match, screen, panel)
            WidgetRendererRegistry.render(match.rootWidget, graphics, mouseX, mouseY, delta)
            EventDispatcher.updateHover(match.rootWidget, mouseX, mouseY)
            TooltipRenderer.renderTooltipPass(match.rootWidget, graphics, mouseX, mouseY)
        }
    }

    /** 是否应取消原版容器背景（renderBg）绘制：任一命中 overlay 声明了 cancel-vanilla-bg 即取消。 */
    fun shouldCancelVanillaBg(screen: Screen): Boolean =
        UiScreenStorage.getOverlaysFor(screen).any { it.cancelVanillaBg }

    /**
     * 背景层通道：渲染 index < 0 的 overlay（容器背景之上、物品/槽位之下）。
     * 由 ContainerBackgroundRenderMixin 在 renderBg 调用点触发；背景层不参与点击与悬停。
     * 槽位重定位声明（slot 节点）也在此通道应用——必须在原版槽位渲染之前写入 Slot.x/y。
     */
    fun renderContainerBackground(screen: Screen, graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (screen is GuiCanvas) return
        val container = screen as? AbstractContainerScreen<*>
        val panel = container as? ContainerPanelBridge
        val overlays = UiScreenStorage.getOverlaysFor(screen)
        if (container != null) SlotLayoutManager.apply(container, panel, overlays)
        for (match in overlays) {
            if (match.index >= 0) continue
            layoutMatch(match, screen, panel)
            WidgetRendererRegistry.render(match.rootWidget, graphics, mouseX, mouseY, delta)
        }
    }

    /**
     * 仅当点击命中 overlay 内的可见控件且事件被消费时才拦截，
     * 其余点击透传给原版界面（背包槽位拖拽等原生交互不受影响）。
     * 背景层（index < 0）纯装饰，不参与点击拦截。
     */
    fun handleClick(screen: Screen, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (screen is GuiCanvas) return false
        var consumed = false
        for (match in UiScreenStorage.getOverlaysFor(screen)) {
            if (match.index < 0) continue
            if (EventDispatcher.dispatchClick(match.rootWidget, mouseX.toInt(), mouseY.toInt(), button)) {
                consumed = true
            }
        }
        return consumed
    }

    internal fun layoutMatch(match: OverlayMatch, screen: Screen, panel: ContainerPanelBridge?) {
        if (match.containerAnchored && panel != null) {
            LayoutCache.getOrCompute(
                match.rootWidget, panel.`lantern$getImageWidth`(), panel.`lantern$getImageHeight`(),
                panel.`lantern$getLeftPos`(), panel.`lantern$getTopPos`()
            )
        } else {
            LayoutCache.getOrCompute(match.rootWidget, screen.width, screen.height)
        }
    }
}
