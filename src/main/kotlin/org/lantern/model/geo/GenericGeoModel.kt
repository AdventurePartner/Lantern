package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel

/**
 * Lantern 实体模型（Fabric 1.21.1 / Forge 1.21.1；1.20.1 与 1.21.10 各有副本）。
 * 每个渲染器（= 每个实体 UUID）持有一份深拷贝骨骼树（IsolatedBones），
 * 骨骼唯一写入方是 AnimationHost，GeckoLib 的动画处理整个置空
 */
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
        // 缓存未命中交给父类抛 GeckoLib 自己的错误（渲染器在进入渲染前已守卫过）
        val shared = GeckoLibCache.getBakedModels()[location] ?: return super.getBakedModel(location)
        val isolated = IsolatedBones.clone(shared)
        // processor 指向克隆骨骼；registerGeoBone 会为每根新骨骼 saveInitialSnapshot
        animationProcessor.setActiveModel(isolated.model)
        isolatedModel = isolated.model
        initialPose = isolated.initialPose
        return isolated.model
    }

    /** GeckoLib 的动画处理会把没被控制器驱动的骨骼插值回初始姿势，覆盖 AnimationHost 写入的姿势 */
    override fun handleAnimations(
        animatable: GenericReplacedEntity<T>,
        instanceId: Long,
        animationState: AnimationState<GenericReplacedEntity<T>>,
        partialTick: Float
    ) = Unit
}
