package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.model.DefaultedEntityGeoModel

class GenericGeoModel<T : Entity>(
    private val wrapper: CustomModelWrapper
) : DefaultedEntityGeoModel<GenericReplacedEntity<T>>(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "default")) {

    override fun getTextureResource(animatable: GenericReplacedEntity<T>?): ResourceLocation {
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }

    override fun getModelResource(animatable: GenericReplacedEntity<T>?): ResourceLocation {
        return wrapper.modelLocation
    }

    override fun getAnimationResource(animatable: GenericReplacedEntity<T>?): ResourceLocation {
        return wrapper.animationLocation
    }
}