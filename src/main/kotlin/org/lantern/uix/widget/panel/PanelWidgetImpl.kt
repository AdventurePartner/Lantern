package org.lantern.uix.widget.panel

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.IComponent
import org.lantern.uix.widget.BaseWidget
import org.lantern.uix.widget.IWidget

/** `panel` 与 `div` 共用的容器；两者只有类型名不同。 */
class PanelWidgetImpl(override val widgetType: String = "panel") : BaseWidget() {

    @get:JvmName("getChildWidgets")
    val children: MutableList<IWidget> = mutableListOf()

    override fun getChildren(): List<IComponent> = children

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
