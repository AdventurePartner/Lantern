package org.lantern.model.block

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.entity.BlockEntity
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import software.bernie.geckolib.renderer.GeoBlockRenderer

class LanternBlockRenderer : GeoBlockRenderer<LanternBlockEntity>(LanternBlockGeoModel()) {

    companion object {
        private val warnedModels = HashSet<ResourceLocation>()

        @JvmStatic
        fun renderMissingBlock(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int
        ) {
            val mc = Minecraft.getInstance()
            val missingModel = mc.modelManager.missingModel
            val texture = net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS
            val buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture))
            mc.blockRenderer.modelRenderer.renderModel(
                poseStack.last(),
                buffer,
                null,
                missingModel,
                1.0f, 1.0f, 1.0f,
                packedLight,
                packedOverlay
            )
        }
    }

    override fun getTextureLocation(animatable: LanternBlockEntity): ResourceLocation {
        val wrapper = animatable.wrapper ?: return super.getTextureLocation(animatable)
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }

    override fun render(
        blockEntity: BlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val lanternBlockEntity = blockEntity as LanternBlockEntity
        val wrapper = lanternBlockEntity.wrapper
        val correctedLight = sampleNeighborLight(lanternBlockEntity, packedLight)

        poseStack.pushPose()
        try {
            if (wrapper != null && wrapper.blockScale != 1.0f) {
                val bs = wrapper.blockScale
                poseStack.translate(0.5, 0.0, 0.5)
                poseStack.scale(bs, bs, bs)
                poseStack.translate(-0.5, 0.0, -0.5)
            }
            super.render(blockEntity, partialTick, poseStack, bufferSource, correctedLight, packedOverlay)
        } catch (e: RuntimeException) {
            val modelLoc = wrapper?.modelLocation
            if (modelLoc != null && warnedModels.add(modelLoc)) {
                Lantern.logger.warn("Block model render failed for {}: {}", modelLoc, e.message)
            }
            renderMissingBlock(poseStack, bufferSource, correctedLight, packedOverlay)
        } finally {
            poseStack.popPose()
        }
    }

    private fun sampleNeighborLight(blockEntity: LanternBlockEntity, originalLight: Int): Int {
        val level = blockEntity.level ?: return originalLight
        val pos = blockEntity.blockPos
        var maxBlock = LightTexture.block(originalLight)
        var maxSky = LightTexture.sky(originalLight)
        for (dir in Direction.entries) {
            val neighbor = pos.relative(dir)
            maxBlock = maxOf(maxBlock, level.getBrightness(LightLayer.BLOCK, neighbor))
            maxSky = maxOf(maxSky, level.getBrightness(LightLayer.SKY, neighbor))
        }
        return LightTexture.pack(maxBlock, maxSky)
    }
}
