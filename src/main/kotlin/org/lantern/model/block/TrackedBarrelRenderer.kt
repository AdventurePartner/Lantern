package org.lantern.model.block

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import org.lantern.Lantern
import org.lantern.model.handler.BlockRendererHandler
import software.bernie.geckolib.cache.GeckoLibCache

/**
 * barrel (open=true) 的 BlockEntityRenderer。
 * 在标准 block entity 渲染阶段执行，Iris/Sodium 完全支持。
 *
 * BUILD_TAG: v20260405-barrel-renderer
 */
class TrackedBarrelRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<BarrelBlockEntity> {

    private val delegate = LanternBlockRenderer()

    @Volatile
    private var logCount = 0

    override fun render(
        blockEntity: BarrelBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val pos = blockEntity.blockPos
        val wrapper = BlockRendererHandler.getWrapperByPos(pos)

        // 前 10 次调用记录详细诊断
        if (logCount < 10) {
            logCount++
            val geoLoc = wrapper?.modelLocation
            val inCache = if (geoLoc != null) GeckoLibCache.getBakedModels().containsKey(geoLoc) else false
            val cacheSize = GeckoLibCache.getBakedModels().size
            Lantern.logger.info(
                "[Lantern] TrackedBarrelRenderer #{} pos={} hasWrapper={} geoLoc={} inGeckoCache={} cacheSize={}",
                logCount, pos, wrapper != null, geoLoc, inCache, cacheSize
            )
        }

        if (wrapper == null) return
        val level = blockEntity.level ?: return

        val lanternBE = BlockRendererHandler.getOrCreateCachedEntity(pos, wrapper, level)

        // 尝试 GeckoLib 渲染，失败时用 entity render type 的 fallback
        try {
            delegate.render(lanternBE, partialTick, poseStack, bufferSource, packedLight, packedOverlay)
        } catch (e: Exception) {
            // delegate.render 内部已经 catch RuntimeException 并调 renderMissingBlock。
            // 这里捕获其他异常，也用 entity-compatible 的 fallback。
            if (logCount <= 10) {
                Lantern.logger.warn("[Lantern] TrackedBarrelRenderer outer catch: {}", e.message)
            }
            renderEntityFallback(poseStack, bufferSource, packedLight)
        }
    }

    /**
     * 用 entity render type 渲染 fallback 紫黑方块。
     * 与 LanternBlockRenderer.renderMissingBlock 不同，这里保证在 BE 渲染阶段可见。
     */
    private fun renderEntityFallback(poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val missingModel = mc.modelManager.missingModel
        @Suppress("DEPRECATION")
        val buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS))
        mc.blockRenderer.modelRenderer.renderModel(
            poseStack.last(), buffer, null, missingModel,
            1.0f, 1.0f, 1.0f, packedLight, OverlayTexture.NO_OVERLAY
        )
    }

    override fun getViewDistance(): Int = 256
}
