package org.lantern.model.block

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.LightLayer
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import software.bernie.geckolib.renderer.GeoBlockRenderer

/**
 * GeckoLib 方块渲染器。
 * 所有自定义方块共用同一个渲染器实例，模型/纹理/动画由 LanternBlockGeoModel 动态决定。
 */
class LanternBlockRenderer : GeoBlockRenderer<LanternBlockEntity>(LanternBlockGeoModel()) {

    companion object {
        private val warnedModels = HashSet<ResourceLocation>()

        /**
         * 渲染 vanilla 黑紫棋盘格缺失模型。
         * 供 BlockEntityRenderer 和 ItemInHandRendererMixin 共用。
         */
        @JvmStatic
        fun renderMissingBlock(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int
        ) {
            val mc = Minecraft.getInstance()
            val missingModel = mc.modelManager.missingModel
            // 使用 entity render type 而非 terrain render type (solid)，
            // 确保在 block entity 渲染阶段能被正确 flush。
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
        blockEntity: LanternBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val wrapper = blockEntity.wrapper

        // 服务端没有客户端 Mixin，蓇菇方块在服务端仍为实心不透光，
        // chunk 包携带的光照值 = 0 → 渲染全黑。
        // 从 6 邻居采样最大光照值作为修正。
        val correctedLight = sampleNeighborLight(blockEntity, packedLight)

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

    /**
     * 服务端将蓇菇方块视为实心不透光，方块位置处光照 = 0。
     * 从 6 个相邻方块采样最大 block light 和 sky light，
     * 确保自定义方块的光照与周围环境一致。
     */
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