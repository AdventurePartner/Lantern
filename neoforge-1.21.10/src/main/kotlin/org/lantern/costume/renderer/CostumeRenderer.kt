package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.state.CameraRenderState
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.geo.CostumeGeoModel
import org.lantern.costume.renderstate.CostumeRenderContext
import org.lantern.costume.renderstate.CostumeRenderData
import org.lantern.costume.wrapper.CostumeModelWrapper
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.GeoRenderState

class CostumeRenderer(
    private val wrapper: CostumeModelWrapper,
    val animatable: CostumeAnimatable
) : GeoObjectRenderer<CostumeAnimatable, CostumeRenderContext, GeoRenderState>(CostumeGeoModel(wrapper)) {
    init {
        scaleWidth = wrapper.scale
        scaleHeight = wrapper.scale
    }

    override fun getInstanceId(animatable: CostumeAnimatable, relatedObject: CostumeRenderContext): Long =
        relatedObject.playerId.mostSignificantBits xor relatedObject.playerId.leastSignificantBits

    override fun addRenderData(
        animatable: CostumeAnimatable,
        relatedObject: CostumeRenderContext,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        renderState.addGeckolibData(CostumeRenderData.PLAYER_POSE, relatedObject.pose)
        renderState.addGeckolibData(CostumeRenderData.ANIMATION_STATE, relatedObject.animationState)
    }

    override fun adjustRenderPose(
        renderState: GeoRenderState,
        poseStack: PoseStack,
        model: BakedGeoModel,
        cameraState: CameraRenderState
    ) {
        poseStack.translate(wrapper.offsetX, wrapper.offsetY, wrapper.offsetZ)
    }
}
