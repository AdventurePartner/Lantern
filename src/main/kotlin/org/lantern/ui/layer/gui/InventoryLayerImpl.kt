package org.lantern.ui.layer.gui

import org.lantern.ui.components.image.ImageComponent
import org.lantern.ui.layer.BaseLayer
import org.lantern.ui.misc.impl.GuiMisc
import org.lantern.ui.misc.impl.ImageMisc
import java.util.UUID

class InventoryLayerImpl : BaseLayer(GuiMisc()) {

    init {
        val misc = this.getMisc() as GuiMisc
        misc.background = "lantern:textures/example/inventory.png"
        misc.width = 256
        misc.height = 195
    }

    override fun getUniqueId(): String {
        return "inventory_layer"
    }

    init {
        val params = ImageMisc()
        params.uniqueId = UUID.randomUUID().toString()
        params.x = -20
        params.y = -120
        params.width = 216
        params.height = 94
        params.background = "lantern:textures/example/logo.png"
        addComponent(ImageComponent(params))
    }
}