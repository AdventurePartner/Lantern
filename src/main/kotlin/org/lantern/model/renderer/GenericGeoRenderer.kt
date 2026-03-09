package org.lantern.model.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.geo.GenericGeoModel
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer

class GenericGeoRenderer<T : Entity>(
    entityType: EntityType<T>,
    private val wrapper: CustomModelWrapper
) : GeoReplacedEntityRenderer<Entity, GenericReplacedEntity<T>>(
    CycleHandler.context,
    GenericGeoModel(wrapper),
    GenericReplacedEntity(entityType)
) {
    // 用于防止重复渲染名字（渲染在主线程执行，无需 @Volatile）
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
        // 每次渲染开始时重置标志位
        isRenderingNameTag = false

        // 将 wrapper 和实体信息传递给 animatable
        (animatable as? GenericReplacedEntity<*>)?.let { replacedEntity ->
            replacedEntity.currentAnimationStates = wrapper.animationStates
            replacedEntity.currentRenderedEntity = entity
        }

        // 调用父类渲染（包括模型和名字）
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
    }

    override fun shouldShowName(arg: Entity): Boolean {
        if (wrapper.hiddenName) {
            return false
        }
        // 防止重复渲染：如果已经在渲染中，跳过
        if (isRenderingNameTag) {
            return false
        }
        // 标记为正在渲染，防止重复
        isRenderingNameTag = true
        return super.shouldShowName(arg)
    }

    override fun renderNameTag(
        entity: Entity,
        component: Component,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        originalOffsetY: Float
    ) {
        if (wrapper.hiddenName) {
            return
        }

        // 使用配置的偏移量，如果为 0 则使用原始值
        val offsetY = if (wrapper.nameTagOffsetY != 0.0F) wrapper.nameTagOffsetY else originalOffsetY

        // 应用 Y 轴偏移：在渲染前平移矩阵
        poseStack.pushPose()
        poseStack.translate(0.0, offsetY.toDouble(), 0.0)
        super.renderNameTag(entity, component, poseStack, bufferSource, packedLight, offsetY)
        poseStack.popPose()
    }

    override fun getTextureLocation(entity: Entity): ResourceLocation {
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }
}