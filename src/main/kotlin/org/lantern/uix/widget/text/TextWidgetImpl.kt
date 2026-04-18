package org.lantern.uix.widget.text

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.widget.BaseWidget
import org.lantern.uix.widget.ITextHolder

class TextWidgetImpl(override var text: String = "") : BaseWidget(), ITextHolder {

    override val widgetType: String = "text"

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
