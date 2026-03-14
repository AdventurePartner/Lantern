package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.renderer.impl.ButtonRenderer
import org.lantern.uix.renderer.impl.ImageRenderer
import org.lantern.uix.renderer.impl.InputRenderer
import org.lantern.uix.renderer.impl.PanelRenderer
import org.lantern.uix.renderer.impl.SlotRenderer
import org.lantern.uix.renderer.impl.TextRenderer
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.IWidget

object WidgetRendererRegistry {

    private val renderers = mutableMapOf<String, IWidgetRenderer<*>>()

    init {
        register("text", TextRenderer)
        register("button", ButtonRenderer)
        register("image", ImageRenderer)
        register("panel", PanelRenderer)
        register("input", InputRenderer)
        register("slot", SlotRenderer)
    }

    fun register(type: String, renderer: IWidgetRenderer<*>) {
        renderers[type] = renderer
    }

    @Suppress("UNCHECKED_CAST")
    fun render(widget: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val renderer = renderers[widget.widgetType] as? IWidgetRenderer<IWidget> ?: return
        renderer.render(widget, widget.style, graphics, mouseX, mouseY, delta)
    }
}
