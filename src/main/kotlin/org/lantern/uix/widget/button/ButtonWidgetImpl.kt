package org.lantern.uix.widget.button

import net.minecraft.client.gui.GuiGraphics
import org.lantern.internal.action.UiActionHandler
import org.lantern.uix.widget.BaseWidget
import org.lantern.uix.widget.ITextHolder

class ButtonWidgetImpl(
    override var text: String = "",
    var action: String = ""
) : BaseWidget(), ITextHolder {

    override val widgetType: String = "button"

    init {
        onClick { event ->
            if (action.isNotBlank()) {
                UiActionHandler.execute(action)
                event.consume()
            }
        }
    }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
