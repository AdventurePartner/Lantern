package org.lantern.costume.geo

import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.ResourceLocation
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.geo.IsolatedBones
import org.lantern.platform.PlayerBridge
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel

/**
 * 外观模型（Fabric 1.21.1 / Forge 1.21.1；1.20.1 与 1.21.10 各有副本）。
 *
 * hostDriven（玩家整替）外观由 Lantern 动画内核驱动：每个玩家的渲染器持有深拷贝骨骼树，
 * 贴图取该玩家的真实皮肤，GeckoLib 的动画处理置空。普通装饰外观维持原来的共享模型与
 * 控制器路径
 */
class CostumeGeoModel(
    private val wrapper: CostumeModelWrapper
) : GeoModel<CostumeAnimatable>() {

    private var isolatedModel: BakedGeoModel? = null

    var initialPose: Map<String, FloatArray> = emptyMap()
        private set

    override fun getModelResource(animatable: CostumeAnimatable?): ResourceLocation = wrapper.modelLocation

    override fun getTextureResource(animatable: CostumeAnimatable?): ResourceLocation {
        if (wrapper.hostDriven) {
            (animatable?.currentEntity as? AbstractClientPlayer)?.let { return PlayerBridge.skinTexture(it) }
        }
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }

    override fun getAnimationResource(animatable: CostumeAnimatable?): ResourceLocation = wrapper.animationLocation

    override fun getBakedModel(location: ResourceLocation): BakedGeoModel {
        if (!wrapper.hostDriven) return super.getBakedModel(location)
        isolatedModel?.let { return it }
        val shared = GeckoLibCache.getBakedModels()[location] ?: return super.getBakedModel(location)
        val isolated = IsolatedBones.clone(shared)
        animationProcessor.setActiveModel(isolated.model)
        isolatedModel = isolated.model
        initialPose = isolated.initialPose
        return isolated.model
    }

    override fun handleAnimations(
        animatable: CostumeAnimatable,
        instanceId: Long,
        animationState: AnimationState<CostumeAnimatable>,
        partialTick: Float
    ) {
        if (wrapper.hostDriven) return
        super.handleAnimations(animatable, instanceId, animationState, partialTick)
    }
}
