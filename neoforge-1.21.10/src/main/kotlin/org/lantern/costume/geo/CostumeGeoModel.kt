package org.lantern.costume.geo

import net.minecraft.resources.ResourceLocation
import org.lantern.costume.bone.BoneRotation
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderstate.CostumeRenderData
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import software.bernie.geckolib.animatable.processing.AnimationState
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

class CostumeGeoModel(
    private val wrapper: CostumeModelWrapper
) : GeoModel<CostumeAnimatable>() {
    override fun getModelResource(renderState: GeoRenderState): ResourceLocation =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: CostumeAnimatable): ResourceLocation =
        GeckoResourceIds.animation(wrapper.animationLocation)

    override fun setCustomAnimations(animationState: AnimationState<CostumeAnimatable>) {
        if (!wrapper.boneSyncEnabled) return
        val snapshot = requireNotNull(animationState.getData(CostumeRenderData.PLAYER_POSE))
        val mapping = wrapper.boneMapping
        applyRotation(mapping.head, snapshot.head)
        applyRotation(mapping.body, snapshot.body)
        applyRotation(mapping.leftArm, snapshot.leftArm)
        applyRotation(mapping.rightArm, snapshot.rightArm)
        applyRotation(mapping.leftLeg, snapshot.leftLeg)
        applyRotation(mapping.rightLeg, snapshot.rightLeg)
    }

    private fun applyRotation(name: String, rotation: BoneRotation) {
        animationProcessor.getBone(name)?.updateRotation(rotation.x, rotation.y, rotation.z)
    }
}
