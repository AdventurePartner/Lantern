package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.core.animation.AnimationState
import software.bernie.geckolib.model.GeoModel

/** 共享 GenericGeoModel 的 1.20.1 版：handleAnimations 少一个 partialTick 参数 */
class GenericGeoModel<T : Entity>(
    private val wrapper: CustomModelWrapper
) : GeoModel<GenericReplacedEntity<T>>() {

    private var isolatedModel: BakedGeoModel? = null

    var initialPose: Map<String, FloatArray> = emptyMap()
        private set

    override fun getModelResource(animatable: GenericReplacedEntity<T>?): ResourceLocation = wrapper.modelLocation

    override fun getTextureResource(animatable: GenericReplacedEntity<T>?): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: GenericReplacedEntity<T>?): ResourceLocation =
        wrapper.animationLocation

    override fun getBakedModel(location: ResourceLocation): BakedGeoModel {
        isolatedModel?.let { return it }
        val shared = GeckoLibCache.getBakedModels()[location] ?: return super.getBakedModel(location)
        val isolated = IsolatedBones.clone(shared)
        animationProcessor.setActiveModel(isolated.model)
        isolatedModel = isolated.model
        initialPose = isolated.initialPose
        return isolated.model
    }

    override fun handleAnimations(
        animatable: GenericReplacedEntity<T>,
        instanceId: Long,
        animationState: AnimationState<GenericReplacedEntity<T>>
    ) = Unit
}
