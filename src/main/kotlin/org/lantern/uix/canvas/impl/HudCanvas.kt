package org.lantern.uix.canvas.impl

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.canvas.BaseCanvas
import org.lantern.uix.properties.impl.ImageProperties
import org.lantern.uix.widget.image.ImageWidgetImpl

class HudCanvas : BaseCanvas() {

    init {
        addWidget(ImageWidgetImpl(ImageProperties()))
    }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}