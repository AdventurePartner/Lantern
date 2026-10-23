package org.lantern.model.geo

import net.minecraft.resources.Identifier
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

/**
 * 实体模型。GeckoLib 5.4 起骨骼不可变、姿势只在本次绘制里经 BoneSnapshot 挂上，
 * 同名模型的多个实体共用缓存里的骨骼树也互不覆盖，不再需要 5.3 时按渲染器深拷贝骨骼树。
 * 姿势写入在 GenericGeoRenderer.adjustModelBonesForRender，GeckoLib 自己的控制器不参与
 */
class GenericGeoModel(
    private val wrapper: CustomModelWrapper
) : GeoModel<GenericReplacedEntity>() {

    override fun getModelResource(renderState: GeoRenderState): Identifier =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): Identifier =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: GenericReplacedEntity): Identifier =
        GeckoResourceIds.animation(wrapper.animationLocation)
}
