package org.lantern.internal.handler

import org.lantern.internal.action.UiActionHandler
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.input.FocusManager
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.button.ButtonWidgetImpl
import org.lantern.uix.widget.input.InputWidgetImpl
import org.lantern.uix.widget.panel.PanelWidgetImpl

object HudClickDispatcher {

    fun dispatch(mouseX: Int, mouseY: Int) {
        FocusManager.blur()
        UiScreenStorage.getAllOfType(ScreenType.HUD).values.forEach { root ->
            dispatchClick(root, mouseX, mouseY)
        }
    }

    /**
     * Recursively walks the widget tree using the same coordinate system as PanelRenderer:
     * each panel subtracts its own x/y from the cursor before passing down to children.
     */
    fun dispatchClick(widget: IWidget, mouseX: Int, mouseY: Int): Boolean {
        if (widget is PanelWidgetImpl) {
            val px = widget.style.getInt(StyleProperty.X)
            val py = widget.style.getInt(StyleProperty.Y)
            for (child in widget.children) {
                if (dispatchClick(child, mouseX - px, mouseY - py)) return true
            }
        }
        if (widget is ButtonWidgetImpl && widget.style.getBoolean(StyleProperty.VISIBLE)) {
            val x = widget.style.getInt(StyleProperty.X)
            val y = widget.style.getInt(StyleProperty.Y)
            val w = widget.style.getInt(StyleProperty.WIDTH, 80)
            val h = widget.style.getInt(StyleProperty.HEIGHT, 20)
            if (mouseX in x until x + w && mouseY in y until y + h) {
                UiActionHandler.execute(widget.action)
                return true
            }
        }
        if (widget is InputWidgetImpl && widget.style.getBoolean(StyleProperty.VISIBLE)) {
            val x = widget.style.getInt(StyleProperty.X)
            val y = widget.style.getInt(StyleProperty.Y)
            val w = widget.style.getInt(StyleProperty.WIDTH, 120)
            val h = widget.style.getInt(StyleProperty.HEIGHT, 20)
            if (mouseX in x until x + w && mouseY in y until y + h) {
                FocusManager.focus(widget)
                return true
            }
        }
        return false
    }
}
