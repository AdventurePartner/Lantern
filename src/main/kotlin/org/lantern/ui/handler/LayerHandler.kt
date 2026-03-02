package org.lantern.ui.handler

import org.lantern.ui.layer.ILayer
import org.lantern.ui.layer.gui.InventoryLayerImpl
import org.lantern.ui.layer.hud.HudLayerImpl

object LayerHandler {
    private val layers = mutableMapOf<String, ILayer>()
    val hudLayer = HudLayerImpl()
    val playerInvLayer = InventoryLayerImpl()

    fun addLayer(uniqueId: String, layer: ILayer) {
        layers[uniqueId] = layer
    }
}