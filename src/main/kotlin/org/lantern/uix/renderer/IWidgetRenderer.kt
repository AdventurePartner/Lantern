package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.IWidget

/**
 * Stateless renderer for a specific widget type.
 * Decoupled from the widget itself so the style pipeline can be swapped independently.
 */
interface IWidgetRenderer<T : IWidget> {

    fun render(widget: T, style: StyleRule, graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float)
}
