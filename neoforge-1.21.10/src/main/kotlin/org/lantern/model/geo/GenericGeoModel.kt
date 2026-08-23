package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

class GenericGeoModel(
    private val wrapper: CustomModelWrapper
) : GeoModel<GenericReplacedEntity>() {
    override fun getModelResource(renderState: GeoRenderState): ResourceLocation =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: GenericReplacedEntity): ResourceLocation =
        GeckoResourceIds.animation(wrapper.animationLocation)
}
