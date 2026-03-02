package org.lantern.ui.layer.gui

import org.lantern.ui.layer.BaseLayer
import org.lantern.ui.layer.ILayer
import org.lantern.ui.misc.impl.GuiMisc

class GuiLayerImpl(private val guiParams: GuiMisc) : BaseLayer(guiParams), ILayer {

    override fun getUniqueId(): String {
        return guiParams.uniqueId
    }
}