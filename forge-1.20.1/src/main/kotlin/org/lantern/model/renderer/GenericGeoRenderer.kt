package org.lantern.model.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import org.lantern.Lantern
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.geo.GenericGeoModel
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer

class GenericGeoRenderer<T : Entity>(
    entityType: EntityType<T>,
    private val wrapper: CustomModelWrapper
) : GeoReplacedEntityRenderer<Entity, GenericReplacedEntity<T>>(
    CycleHandler.context,
    GenericGeoModel(wrapper),
    GenericReplacedEntity(entityType)
) {
    companion object {
        private val warnedModels = HashSet<ResourceLocation>()
    }

    private var isRenderingNameTag = false

    init {
        scaleHeight = wrapper.scale
        scaleWidth = wrapper.scale
    }

    override fun render(
        entity: Entity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        isRenderingNameTag = false

        (animatable as? GenericReplacedEntity<*>)?.let { replacedEntity ->
            replacedEntity.currentAnimationStates = wrapper.animationStates
            replacedEntity.currentRenderedEntity = entity
        }

        if (GeckoLibCache.getBakedModels()[wrapper.modelLocation] == null) {
            if (warnedModels.add(wrapper.modelLocation)) {
                Lantern.logger.warn("[Lantern] Entity model not yet cached, deferring render: {}", wrapper.modelLocation)
            }
            return
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
    }

    override fun shouldShowName(arg: Entity): Boolean {
        if (wrapper.hiddenName) {
            return false
        }
        if (isRenderingNameTag) {
            return false
        }
        isRenderingNameTag = true
        return super.shouldShowName(arg)
    }

    override fun renderNameTag(
        entity: Entity,
        component: Component,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        if (wrapper.hiddenName) {
            return
        }
        super.renderNameTag(entity, component, poseStack, bufferSource, packedLight)
    }

    override fun getTextureLocation(entity: Entity): ResourceLocation {
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }
}
