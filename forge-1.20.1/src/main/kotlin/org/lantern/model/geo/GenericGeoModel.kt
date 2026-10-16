package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.core.animation.AnimationState
import software.bernie.geckolib.model.GeoModel

/**
 * 共享 GenericGeoModel 的 1.20.1 版：GeoBone 只有逐轴 setter、handleAnimations 少一个参数。
 * 每个渲染器深拷贝一份骨骼树隔离，GeckoLib 的动画处理整个置空，骨骼只由 AnimationHost 写
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
        val shared = GeckoLibCache.getBakedModels()[location] ?: return super.getBakedModel(location)
        val copies = shared.topLevelBones().map { cloneBone(it, null) }
        val clone = BakedGeoModel(copies, shared.properties())
        animationProcessor.setActiveModel(clone)
        isolatedModel = clone
        initialPose = HashMap<String, FloatArray>().also { captureInitial(copies, it) }
        return clone
    }

    private fun captureInitial(bones: List<GeoBone>, out: MutableMap<String, FloatArray>) {
        for (bone in bones) {
            out[bone.name] = floatArrayOf(
                bone.rotX, bone.rotY, bone.rotZ,
                bone.posX, bone.posY, bone.posZ,
                bone.scaleX, bone.scaleY, bone.scaleZ
            )
            captureInitial(bone.childBones, out)
        }
    }

    private fun cloneBone(source: GeoBone, parent: GeoBone?): GeoBone {
        val copy = GeoBone(parent, source.name, source.mirror, source.inflate, source.shouldNeverRender(), source.reset)
        copy.pivotX = source.pivotX
        copy.pivotY = source.pivotY
        copy.pivotZ = source.pivotZ
        val init = source.initialSnapshot
        if (init != null) {
            copy.rotX = init.rotX
            copy.rotY = init.rotY
            copy.rotZ = init.rotZ
            copy.posX = init.offsetX
            copy.posY = init.offsetY
            copy.posZ = init.offsetZ
            copy.scaleX = init.scaleX
            copy.scaleY = init.scaleY
            copy.scaleZ = init.scaleZ
        } else {
            copy.rotX = source.rotX
            copy.rotY = source.rotY
            copy.rotZ = source.rotZ
        }
        copy.cubes.addAll(source.cubes)
        for (child in source.childBones) {
            copy.childBones.add(cloneBone(child, copy))
        }
        return copy
    }

    override fun handleAnimations(
        animatable: GenericReplacedEntity<T>,
        instanceId: Long,
        animationState: AnimationState<GenericReplacedEntity<T>>
    ) = Unit
}
