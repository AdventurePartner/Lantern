package org.lantern.uix.canvas.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lantern.internal.action.UiActionHandler
import org.lantern.internal.handler.HudClickDispatcher
import org.lantern.uix.input.FocusManager
import org.lantern.uix.renderer.WidgetRendererRegistry
import org.lantern.uix.widget.IWidget

class GuiCanvas(
    private val screenId: String,
    private val rootWidget: IWidget
) : Screen(Component.literal(screenId)) {

    /**
     * No-op: prevents vanilla blur post-process and any mod hooks on renderBackground
     * from covering our UI. The dim overlay is drawn manually in render().
     */
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Manual dim overlay — drawn directly without triggering blur hooks
        guiGraphics.fill(0, 0, width, height, 0x80000000.toInt())
        WidgetRendererRegistry.render(rootWidget, guiGraphics, mouseX, mouseY, partialTick)
        // Intentionally not calling super.render() to avoid re-triggering renderBackground
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            FocusManager.blur()
            HudClickDispatcher.dispatchClick(rootWidget, mouseX.toInt(), mouseY.toInt())
        }
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
