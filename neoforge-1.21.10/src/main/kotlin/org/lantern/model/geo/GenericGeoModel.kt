package org.lantern.model.geo

import net.minecraft.resources.ResourceLocation
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.cache.GeckoLibResources
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

class GenericGeoModel(
    private val wrapper: CustomModelWrapper
) : GeoModel<GenericReplacedEntity>() {
    /**
     * 本模型实例私有的骨骼树。GeckoLibResources 缓存中的 BakedGeoModel 及其全部
     * GeoBone 实例按模型 ID 全局共享，而渲染管线（GeoRenderer.buildRenderTask）
     * 直接遍历 getBakedModel 返回值的 topLevelBones 绘制。同名模型的多实体若共用
     * 这棵树，后提交实体的姿势写入会覆盖先提交的（A 的动作播在 B 上、死亡倒地同步）。
     * 每个渲染器（=每个实体 UUID）创建时深拷贝一份骨骼树即可彻底隔离。
     */
    private var isolatedModel: BakedGeoModel? = null

    override fun getModelResource(renderState: GeoRenderState): ResourceLocation =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: GenericReplacedEntity): ResourceLocation =
        GeckoResourceIds.animation(wrapper.animationLocation)

    override fun getBakedModel(location: ResourceLocation): BakedGeoModel {
        val shared = GeckoLibResources.getBakedModels()[location]
            ?: return super.getBakedModel(location)
        isolatedModel?.let { return it }
        val clone = BakedGeoModel(
            shared.topLevelBones().map { cloneBone(it, null) },
            shared.properties()
        )
        // processor 同步指向克隆骨骼；registerGeoBone 会为每根新骨骼 saveInitialSnapshot
        getAnimationProcessor().setActiveModel(clone)
        isolatedModel = clone
        return clone
    }

    private fun cloneBone(source: GeoBone, parent: GeoBone?): GeoBone {
        val copy = GeoBone(
            parent,
            source.name,
            source.mirror,
            source.inflate,
            source.shouldNeverRender(),
            source.reset
        )
        // pivot 是加载器烘焙的静态值，动画管线从不修改，直接读当前值安全
        copy.updatePivot(source.pivotX, source.pivotY, source.pivotZ)
        // 初始旋转必须取 initialSnapshot：共享骨骼的当前 rot/pos/scale 每帧被姿势写入污染。
        // initialSnapshot 为空说明该骨骼从未被注册过（也就从未被写过），当前值即静态值
        val init = source.initialSnapshot
        if (init != null) {
            copy.updateRotation(init.rotX, init.rotY, init.rotZ)
            copy.updatePosition(init.offsetX, init.offsetY, init.offsetZ)
            copy.updateScale(init.scaleX, init.scaleY, init.scaleZ)
        } else {
            copy.updateRotation(source.rotX, source.rotY, source.rotZ)
        }
        // GeoCube 持有烘焙好的不可变顶点数据，跨模型共享引用安全
        copy.cubes.addAll(source.cubes)
        for (child in source.childBones) {
            copy.childBones.add(cloneBone(child, copy))
        }
        return copy
    }

    /**
     * 禁用 GeckoLib 的动画处理：即使零控制器注册，handleAnimations / prepareForRenderPass
     * 管线仍可能重置骨骼变换（把 AnimationHost 写入的姿势拉回默认姿势）。
     * 骨骼唯一写入方是 AnimationHost（见 GenericGeoRenderer.addRenderData）。
     */
    override fun handleAnimations(state: software.bernie.geckolib.animatable.processing.AnimationState<GenericReplacedEntity>) {
        // no-op
    }

    override fun prepareForRenderPass(
        animatable: GenericReplacedEntity,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        // 不调 super：跳过 GeckoLib 的动画处理管线，防止骨骼被重置
    }
}
