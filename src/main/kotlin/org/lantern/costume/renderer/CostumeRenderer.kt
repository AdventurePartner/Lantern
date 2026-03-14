package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.geo.CostumeGeoModel
import org.lantern.costume.wrapper.CostumeModelWrapper
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer

class CostumeRenderer(
    private val wrapper: CostumeModelWrapper,
    val animatable: CostumeAnimatable
) : GeoObjectRenderer<CostumeAnimatable>(CostumeGeoModel(wrapper)) {

    init {
        scaleWidth = wrapper.scale
        scaleHeight = wrapper.scale
    }

    override fun getInstanceId(animatable: CostumeAnimatable): Long {
        return this.animatable.currentEntity?.id?.toLong() ?: animatable.hashCode().toLong()
    }

    override fun getRenderType(
        animatable: CostumeAnimatable,
        texture: ResourceLocation,
        bufferSource: MultiBufferSource?,
        partialTick: Float
    ): RenderType {
        return RenderType.entityTranslucent(texture)
    }

    override fun preRender(
        poseStack: PoseStack,
        animatable: CostumeAnimatable,
        model: BakedGeoModel,
        bufferSource: MultiBufferSource?,
        buffer: VertexConsumer?,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        colour: Int
    ) {
        objectRenderTranslations = Matrix4f(poseStack.last().pose())
        scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay)
        // Apply costume offset (no default 0.5/0.51/0.5 block-center offset)
        poseStack.translate(wrapper.offsetX.toDouble(), wrapper.offsetY.toDouble(), wrapper.offsetZ.toDouble())
    }

    fun render(
        entity: Entity,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        partialTick: Float
    ) {
        animatable.currentEntity = entity
        animatable.currentAnimationStates = wrapper.animationStates
        render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick)
    }
}
