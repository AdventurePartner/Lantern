package org.lantern.uix.widget.panel

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.widget.BaseWidget
import org.lantern.uix.widget.IWidget

class PanelWidgetImpl : BaseWidget() {

    override val widgetType: String = "panel"

    val children: MutableList<IWidget> = mutableListOf()

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
