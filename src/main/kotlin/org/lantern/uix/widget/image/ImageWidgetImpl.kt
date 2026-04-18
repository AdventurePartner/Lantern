package org.lantern.uix.widget.image

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.properties.IProperties
import org.lantern.uix.widget.BaseWidget

class ImageWidgetImpl(properties: IProperties) : BaseWidget() {

    override val widgetType: String = "image"

    var texture: String = ""

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        // Rendering handled by ImageRenderer via the canvas system
    }
}