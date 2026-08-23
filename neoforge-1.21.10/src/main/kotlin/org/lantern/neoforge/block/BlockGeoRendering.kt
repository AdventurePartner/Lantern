package org.lantern.neoforge.block

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.resources.model.ModelBakery
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.resources.ResourceLocation
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import org.lantern.model.wrapper.BlockModelWrapper
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animatable.processing.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.GeoRenderState
import software.bernie.geckolib.renderer.base.RenderModelPositioner
import software.bernie.geckolib.constant.dataticket.DataTicket
import software.bernie.geckolib.util.ClientUtil
import software.bernie.geckolib.util.GeckoLibUtil

internal data class BlockRenderContext(
    val instanceId: Long,
    val crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
)

internal class BlockAnimatable(private val wrapper: BlockModelWrapper) : GeoAnimatable {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        if (wrapper.animationLocation == null) return
        val animation = RawAnimation.begin().thenLoop(wrapper.idleAnimation)
        controllers.add(
            AnimationController<BlockAnimatable>("block_idle", 0) { test ->
                test.setAndContinue(animation)
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(obj: Any?): Double = ClientUtil.getCurrentTick()
}

private class BlockGeoModel(
    private val wrapper: BlockModelWrapper
) : GeoModel<BlockAnimatable>() {
    override fun getModelResource(renderState: GeoRenderState): ResourceLocation =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: BlockAnimatable): ResourceLocation =
        GeckoResourceIds.animation(wrapper.animationLocation ?: wrapper.modelLocation)
}

internal class BlockGeoRenderer(wrapper: BlockModelWrapper) :
    GeoObjectRenderer<BlockAnimatable, BlockRenderContext, GeoRenderState>(BlockGeoModel(wrapper)) {
    override fun getInstanceId(animatable: BlockAnimatable, relatedObject: BlockRenderContext): Long =
        relatedObject.instanceId

    override fun addRenderData(
        animatable: BlockAnimatable,
        relatedObject: BlockRenderContext,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        renderState.addGeckolibData(CRUMBLING_OVERLAY, relatedObject.crumblingOverlay)
    }

    override fun adjustRenderPose(
        renderState: GeoRenderState,
        poseStack: PoseStack,
        model: BakedGeoModel,
        cameraState: CameraRenderState
    ) {
        poseStack.translate(0.5, 0.0, 0.5)
    }

    override fun buildRenderTask(
        renderState: GeoRenderState,
        poseStack: PoseStack,
        bakedModel: BakedGeoModel,
        model: GeoModel<BlockAnimatable>,
        renderTasks: OrderedSubmitNodeCollector,
        cameraState: CameraRenderState,
        renderType: RenderType?,
        packedLight: Int,
        packedOverlay: Int,
        renderColor: Int,
        modelPositioner: RenderModelPositioner<GeoRenderState>?
    ) {
        super.buildRenderTask(
            renderState,
            poseStack,
            bakedModel,
            model,
            renderTasks,
            cameraState,
            renderType,
            packedLight,
            packedOverlay,
            renderColor,
            modelPositioner
        )

        val crumblingOverlay = renderState.getGeckolibData(CRUMBLING_OVERLAY) ?: return
        if (renderType?.affectsCrumbling() != true) return
        val positioner = RenderModelPositioner.add(modelPositioner) { state, _ ->
            model.handleAnimations(createAnimationState(state))
        }
        renderTasks.submitCustomGeometry(
            poseStack,
            ModelBakery.DESTROY_TYPES[crumblingOverlay.progress()]
        ) { pose, vertexConsumer ->
            val crumblingPose = PoseStack()
            crumblingPose.last().set(pose)
            positioner.run(renderState, bakedModel)
            val decal = SheetedDecalTextureGenerator(
                vertexConsumer,
                crumblingOverlay.cameraPose(),
                1.0f
            )
            bakedModel.topLevelBones().forEach { bone ->
                renderBone(
                    renderState,
                    crumblingPose,
                    bone,
                    decal,
                    cameraState,
                    packedLight,
                    packedOverlay,
                    renderColor
                )
            }
        }
    }

    private companion object {
        val CRUMBLING_OVERLAY: DataTicket<ModelFeatureRenderer.CrumblingOverlay> = DataTicket.create(
            "lantern_block_crumbling_overlay",
            ModelFeatureRenderer.CrumblingOverlay::class.java
        )
    }
}
