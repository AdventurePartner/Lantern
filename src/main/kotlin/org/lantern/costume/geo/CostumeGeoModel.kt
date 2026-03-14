package org.lantern.costume.geo

import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import software.bernie.geckolib.model.GeoModel

class CostumeGeoModel(
    private val wrapper: CostumeModelWrapper
) : GeoModel<CostumeAnimatable>() {

    override fun getModelResource(animatable: CostumeAnimatable?): ResourceLocation {
        return wrapper.modelLocation
    }

    override fun getTextureResource(animatable: CostumeAnimatable?): ResourceLocation {
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }

    override fun getAnimationResource(animatable: CostumeAnimatable?): ResourceLocation {
        return wrapper.animationLocation
    }
}
