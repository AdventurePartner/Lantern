package org.lantern.uix.renderer

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.layout.Overflow
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.renderer.impl.ButtonRenderer
import org.lantern.uix.renderer.impl.HotbarSelectionRenderer
import org.lantern.uix.renderer.impl.IconBarRenderer
import org.lantern.uix.renderer.impl.ImageRenderer
import org.lantern.uix.renderer.impl.InputRenderer
import org.lantern.uix.renderer.impl.PanelRenderer
import org.lantern.uix.renderer.impl.ProgressBarRenderer
import org.lantern.uix.renderer.impl.SlotRenderer
import org.lantern.uix.renderer.impl.TextRenderer
import org.lantern.uix.renderer.impl.VanillaElementRenderer
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.IWidget

object WidgetRendererRegistry {

    private val renderers = mutableMapOf<String, IWidgetRenderer<*>>()

    init {
        register("text", TextRenderer)
        register("button", ButtonRenderer)
        register("image", ImageRenderer)
        register("panel", PanelRenderer)
        register("div", PanelRenderer)
        register("input", InputRenderer)
        register("slot", SlotRenderer)
        register("hotbar-selection", HotbarSelectionRenderer)
        register("vanilla", VanillaElementRenderer)
        register("icon-bar", IconBarRenderer)
        register("progress-bar", ProgressBarRenderer)
    }

    fun register(type: String, renderer: IWidgetRenderer<*>) {
        renderers[type] = renderer
    }

    @Suppress("UNCHECKED_CAST")
    fun render(widget: IWidget, graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val renderer = renderers[widget.widgetType] as? IWidgetRenderer<IWidget> ?: return
        if (!Overflow.isHidden(widget)) {
            renderer.render(widget, widget.style, graphics, mouseX, mouseY, delta)
            return
        }
        // 空裁切矩形在各版本上的处理不一致，整块不可见时直接不画
        val box = WidgetGeometry.box(widget)
        if (box.width <= 0 || box.height <= 0) return
        ScissorClip.push(graphics, box.x, box.y, box.width, box.height)
        try {
            renderer.render(widget, widget.style, graphics, mouseX, mouseY, delta)
        } finally {
            ScissorClip.pop(graphics)
        }
    }
}
