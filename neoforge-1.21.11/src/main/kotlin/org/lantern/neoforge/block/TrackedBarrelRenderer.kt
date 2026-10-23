package org.lantern.neoforge.block

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.Direction
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.phys.Vec3
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.wrapper.BlockModelWrapper

internal class TrackedBarrelRenderState : BlockEntityRenderState() {
    var wrapper: BlockModelWrapper? = null
    var partialTick: Float = 0.0f
}

internal class TrackedBarrelRenderer : BlockEntityRenderer<BarrelBlockEntity, TrackedBarrelRenderState> {
    override fun createRenderState(): TrackedBarrelRenderState = TrackedBarrelRenderState()

    override fun extractRenderState(
        blockEntity: BarrelBlockEntity,
        renderState: TrackedBarrelRenderState,
        partialTick: Float,
        cameraPos: Vec3,
        damageOverlayState: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, damageOverlayState)
        renderState.wrapper = BlockRendererHandler.getWrapperByPos(blockEntity.blockPos)
        renderState.partialTick = partialTick

        if (renderState.wrapper != null) {
            val level = blockEntity.level ?: return
            var blockLight = LightTexture.block(renderState.lightCoords)
            var skyLight = LightTexture.sky(renderState.lightCoords)
            for (direction in Direction.entries) {
                val neighbor = blockEntity.blockPos.relative(direction)
                blockLight = maxOf(blockLight, level.getBrightness(LightLayer.BLOCK, neighbor))
                skyLight = maxOf(skyLight, level.getBrightness(LightLayer.SKY, neighbor))
            }
            renderState.lightCoords = LightTexture.pack(blockLight, skyLight)
        }
    }

    override fun submit(
        renderState: TrackedBarrelRenderState,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        val wrapper = renderState.wrapper ?: return
        poseStack.pushPose()
        try {
            if (wrapper.blockScale != 1.0f) {
                poseStack.translate(0.5, 0.0, 0.5)
                poseStack.scale(wrapper.blockScale, wrapper.blockScale, wrapper.blockScale)
                poseStack.translate(-0.5, 0.0, -0.5)
            }
            BlockRendererHandler.submit(
                wrapper,
                renderState.blockPos.asLong(),
                poseStack,
                submitNodes,
                cameraState,
                renderState.lightCoords,
                renderState.partialTick,
                renderState.breakProgress
            )
        } finally {
            poseStack.popPose()
        }
    }

    override fun getViewDistance(): Int = 256
}
