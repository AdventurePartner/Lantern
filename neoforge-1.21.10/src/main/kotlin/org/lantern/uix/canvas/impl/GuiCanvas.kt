package org.lantern.uix.canvas.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
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
        override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        }
    }

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

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0x80000000.toInt())
        LayoutCache.getOrCompute(rootWidget, width, height)
        WidgetRendererRegistry.render(rootWidget, graphics, mouseX, mouseY, partialTick)
        EventDispatcher.updateHover(this, mouseX, mouseY)
        TooltipRenderer.renderTooltipPass(rootWidget, graphics, mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            FocusManager.blur()
        }
        EventDispatcher.dispatchClick(this, event.x().toInt(), event.y().toInt(), event.button())
        return super.mouseClicked(event, doubleClick)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val codePoint = event.codepoint()
        if (codePoint <= Char.MAX_VALUE.code && FocusManager.handleChar(codePoint.toChar())) {
            return true
        }
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (FocusManager.handleKey(event.key())) {
            return true
        }
        return super.keyPressed(event)
    }

    override fun removed() {
        FocusManager.blur()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false
}
