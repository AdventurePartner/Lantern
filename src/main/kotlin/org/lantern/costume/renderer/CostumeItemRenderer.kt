package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.geo.CostumeGeoModel
import org.lantern.costume.wrapper.CostumeModelWrapper
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer

class CostumeItemRenderer(
    private val wrapper: CostumeModelWrapper
) : GeoObjectRenderer<CostumeAnimatable>(CostumeGeoModel(wrapper)) {

    private val animatable = CostumeAnimatable()
    private val itemScale = wrapper.scale * 0.5F

    init {
        scaleWidth = itemScale
        scaleHeight = itemScale
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
    }

    fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        partialTick: Float
    ) {
        animatable.currentEntity = null
        animatable.currentAnimationStates = wrapper.animationStates
        render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick)
    }
}
