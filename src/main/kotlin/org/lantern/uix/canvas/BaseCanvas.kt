package org.lantern.uix.canvas

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.BaseComponent
import org.lantern.uix.widget.IWidget

abstract class BaseCanvas : BaseComponent(), ICanvas {
    // 当前子控件列表
    protected val widgets = mutableListOf<IWidget>()

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        width = arg.guiWidth()
        height = arg.guiHeight()
        super.render(arg, i, j, f)

        widgets.forEach { it.render(arg, i, j, f) }
    }

    override fun addWidget(widget: IWidget) {
        widget.parent = this
        this.widgets.add(widget)
    }
}