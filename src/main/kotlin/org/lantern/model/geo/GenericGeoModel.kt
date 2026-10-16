package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.model.GeoModel

/**
 * Lantern 实体模型（Fabric 1.21.1 / Forge 1.21.1；1.20.1 与 1.21.10 各有副本）。
 *
 * GeckoLibCache 里的 BakedGeoModel 及其 GeoBone 按模型 ID 全局共享，同名模型的多个实体
 * 若共用这棵树，后渲染实体的姿势会覆盖先渲染的。每个渲染器（= 每个实体 UUID）创建时
 * 深拷贝一份骨骼树隔离；骨骼唯一写入方是 AnimationHost，GeckoLib 的动画处理整个置空
 */
class GenericGeoModel<T : Entity>(
    private val wrapper: CustomModelWrapper
) : GeoModel<GenericReplacedEntity<T>>() {

    private var isolatedModel: BakedGeoModel? = null

    /** 克隆骨骼的静态初始姿势（rot/pos/scale 各 3 轴），写骨骼时剪辑没驱动到的轴回落到它 */
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
        val copies = shared.topLevelBones().map { cloneBone(it, null) }
        val clone = BakedGeoModel(copies, shared.properties())
        // processor 指向克隆骨骼；registerGeoBone 会为每根新骨骼 saveInitialSnapshot
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
        copy.updatePivot(source.pivotX, source.pivotY, source.pivotZ)
        // 初始变换取 initialSnapshot：共享骨骼的当前值可能已被别的渲染器写过
        val init = source.initialSnapshot
        if (init != null) {
            copy.updateRotation(init.rotX, init.rotY, init.rotZ)
            copy.updatePosition(init.offsetX, init.offsetY, init.offsetZ)
            copy.updateScale(init.scaleX, init.scaleY, init.scaleZ)
        } else {
            copy.updateRotation(source.rotX, source.rotY, source.rotZ)
        }
        // GeoCube 是烘焙好的不可变顶点数据，跨模型共享引用安全
        copy.cubes.addAll(source.cubes)
        for (child in source.childBones) {
            copy.childBones.add(cloneBone(child, copy))
        }
        return copy
    }

    /** GeckoLib 的动画处理会把没被控制器驱动的骨骼插值回初始姿势，覆盖 AnimationHost 写入的姿势 */
    override fun handleAnimations(
        animatable: GenericReplacedEntity<T>,
        instanceId: Long,
        animationState: AnimationState<GenericReplacedEntity<T>>,
        partialTick: Float
    ) = Unit
}
