package org.lantern.neoforge.block

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.resources.model.ModelBakery
import net.minecraft.resources.Identifier
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import org.lantern.model.wrapper.BlockModelWrapper
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.constant.dataticket.DataTicket
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.GeoRenderState
import software.bernie.geckolib.renderer.base.RenderPassInfo
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
}

private class BlockGeoModel(
    private val wrapper: BlockModelWrapper
) : GeoModel<BlockAnimatable>() {
    override fun getModelResource(renderState: GeoRenderState): Identifier =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): Identifier =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: BlockAnimatable): Identifier =
        GeckoResourceIds.animation(wrapper.animationLocation ?: wrapper.modelLocation)
}

internal class BlockGeoRenderer(wrapper: BlockModelWrapper) :
    GeoObjectRenderer<BlockAnimatable, BlockRenderContext, GeoRenderState>(BlockGeoModel(wrapper)) {
    override fun getInstanceId(animatable: BlockAnimatable, relatedObject: BlockRenderContext?): Long =
        relatedObject?.instanceId ?: 0L

    override fun addRenderData(
        animatable: BlockAnimatable,
        relatedObject: BlockRenderContext?,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        relatedObject?.crumblingOverlay?.let { renderState.addGeckolibData(CRUMBLING_OVERLAY, it) }
    }

    override fun adjustRenderPose(renderPassInfo: RenderPassInfo<GeoRenderState>) {
        renderPassInfo.poseStack().translate(0.5, 0.0, 0.5)
    }

    /** 挖掘裂纹：本体照常提交后，用同一套骨骼快照再画一遍贴花 */
    override fun submitRenderTasks(
        renderPassInfo: RenderPassInfo<GeoRenderState>,
        renderTasks: OrderedSubmitNodeCollector,
        renderType: RenderType?
    ) {
        super.submitRenderTasks(renderPassInfo, renderTasks, renderType)

        val crumblingOverlay = renderPassInfo.getGeckolibData(CRUMBLING_OVERLAY) ?: return
        if (renderType?.affectsCrumbling() != true) return
        val packedLight = renderPassInfo.packedLight()
        val packedOverlay = renderPassInfo.packedOverlay()
        val renderColor = renderPassInfo.renderColor()
        renderTasks.submitCustomGeometry(
            renderPassInfo.poseStack(),
            ModelBakery.DESTROY_TYPES[crumblingOverlay.progress()]
        ) { pose, vertexConsumer ->
            val poseStack = renderPassInfo.poseStack()
            val decal = SheetedDecalTextureGenerator(vertexConsumer, crumblingOverlay.cameraPose(), 1.0f)
            poseStack.pushPose()
            poseStack.last().set(pose)
            renderPassInfo.renderPosed {
                renderPassInfo.model().render(renderPassInfo, decal, packedLight, packedOverlay, renderColor)
            }
            poseStack.popPose()
        }
    }

    private companion object {
        val CRUMBLING_OVERLAY: DataTicket<ModelFeatureRenderer.CrumblingOverlay> = DataTicket.create(
            "lantern_block_crumbling_overlay",
            ModelFeatureRenderer.CrumblingOverlay::class.java
        )
    }
}
