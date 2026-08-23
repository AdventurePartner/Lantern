package org.lantern.platform

import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation

object ModelLocationBridge {
    @JvmStatic
    fun inventory(id: ResourceLocation): ModelResourceLocation =
        ModelResourceLocation(id, "inventory")
}

