package org.lantern.model.block

import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import software.bernie.geckolib.model.GeoModel

/**
 * 动态 GeoModel，根据 BlockEntity 的 wrapper 返回不同的模型/纹理/动画资源。
 * 与实体的 GenericGeoModel 同一思路。
 */
class LanternBlockGeoModel : GeoModel<LanternBlockEntity>() {

    companion object {
        // 缺省占位资源（当 wrapper 未设置时）
        private val FALLBACK_MODEL = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "geo/block/fallback.geo.json")
        private val FALLBACK_TEXTURE = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "textures/block/fallback.png")
        private val FALLBACK_ANIMATION = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "animations/block/fallback.animation.json")
    }

    override fun getModelResource(animatable: LanternBlockEntity?): ResourceLocation {
        return animatable?.wrapper?.modelLocation ?: FALLBACK_MODEL
    }

    override fun getTextureResource(animatable: LanternBlockEntity?): ResourceLocation {
        val wrapper = animatable?.wrapper ?: return FALLBACK_TEXTURE
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }

    override fun getAnimationResource(animatable: LanternBlockEntity?): ResourceLocation {
        return animatable?.wrapper?.animationLocation ?: FALLBACK_ANIMATION
    }
}
