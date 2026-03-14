package org.lantern.uix.canvas.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lantern.uix.BaseComponent
import org.lantern.uix.IComponent
import org.lantern.uix.enums.MountPoint
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.event.MouseEvent
import org.lantern.uix.input.FocusManager
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.TooltipRenderer
import org.lantern.uix.renderer.WidgetRendererRegistry
import org.lantern.uix.widget.IWidget

class GuiCanvas(
    private val screenId: String,
    private val rootWidget: IWidget
) : Screen(Component.literal(screenId)), IComponent {

    private val delegate = object : BaseComponent() {
        override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {}
    }

    // region IComponent delegation
    override var x: Int
        get() = delegate.x
        set(value) { delegate.x = value }
    override var y: Int
        get() = delegate.y
        set(value) { delegate.y = value }
    override var width: Int
        get() = super.width
        set(value) { super.width = value }
    override var height: Int
        get() = super.height
        set(value) { super.height = value }
    override var hovered: Boolean
        get() = delegate.hovered
        set(value) { delegate.hovered = value }

    override fun computeMount() = delegate.computeMount()
    override fun getMount(point: MountPoint) = delegate.getMount(point)

    override fun getChildren(): List<IComponent> = listOf(rootWidget)

    override fun onClick(handler: (MouseEvent) -> Unit) = delegate.onClick(handler)
    override fun onMouseEnter(handler: (MouseEvent) -> Unit) = delegate.onMouseEnter(handler)
    override fun onMouseLeave(handler: (MouseEvent) -> Unit) = delegate.onMouseLeave(handler)
    override fun dispatchClick(event: MouseEvent) = delegate.dispatchClick(event)
    override fun dispatchMouseEnter(event: MouseEvent) = delegate.dispatchMouseEnter(event)
    override fun dispatchMouseLeave(event: MouseEvent) = delegate.dispatchMouseLeave(event)
    // endregion

    /**
     * No-op: prevents vanilla blur post-process and any mod hooks on renderBackground
     * from covering our UI. The dim overlay is drawn manually in render().
     */
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val screenWidth = (this as Screen).width
        val screenHeight = (this as Screen).height
        // Manual dim overlay — drawn directly without triggering blur hooks
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0x80000000.toInt())
        LayoutCache.getOrCompute(rootWidget, screenWidth, screenHeight)
        WidgetRendererRegistry.render(rootWidget, guiGraphics, mouseX, mouseY, partialTick)
        EventDispatcher.updateHover(this, mouseX, mouseY)
        TooltipRenderer.renderTooltipPass(rootWidget, guiGraphics, mouseX, mouseY)
        // Intentionally not calling super.render() to avoid re-triggering renderBackground
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            FocusManager.blur()
        }
        EventDispatcher.dispatchClick(this, mouseX.toInt(), mouseY.toInt(), button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (FocusManager.handleChar(codePoint)) return true
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (FocusManager.handleKey(keyCode)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun removed() {
        FocusManager.blur()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false
}
