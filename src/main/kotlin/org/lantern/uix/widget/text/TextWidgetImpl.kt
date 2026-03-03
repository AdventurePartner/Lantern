package org.lantern.uix.widget.text

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.widget.BaseWidget

class TextWidgetImpl(var text: String = "") : BaseWidget() {

    override val widgetType: String = "text"

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
