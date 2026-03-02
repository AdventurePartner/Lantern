package org.lantern.item.model

import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.resources.model.BakedModel
import org.lantern.item.overrides.LanternItemOverrides

class LanternBakedModel(originalModel: BakedModel) : ForwardingBakedModel() {
    init {
        this.wrapped = originalModel
    }

    override fun getOverrides(): ItemOverrides {
        return LanternItemOverrides.INSTANCE
    }
}