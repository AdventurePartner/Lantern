package org.lantern.uix.widget.image

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.uix.enums.MountPoint
import org.lantern.uix.properties.IProperties
import org.lantern.uix.widget.BaseWidget

class ImageWidgetImpl(properties: IProperties) : BaseWidget() {

    override val widgetType: String = "image"

    var texture: String = ""

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        val mount = parent?.getMount(MountPoint.MOUNT_2_MIDDLE) ?: intArrayOf(0, 0)
        arg.blit(
            ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "textures/example/test.png"),
            mount[0] - 18,
            mount[1] - 17,
            36,
            34,
            0F,
            0F,
            36,
            34,
            36,
            34
        )
    }
}