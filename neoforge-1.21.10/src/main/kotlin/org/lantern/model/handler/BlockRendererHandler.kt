package org.lantern.model.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.lantern.Lantern
import org.lantern.model.GeckoResourceIds
import org.lantern.model.wrapper.BlockModelWrapper
import org.lantern.neoforge.block.BlockAnimatable
import org.lantern.neoforge.block.BlockGeoRenderer
import org.lantern.neoforge.block.BlockRenderContext
import software.bernie.geckolib.cache.GeckoLibResources
import java.util.concurrent.ConcurrentHashMap

object BlockRendererHandler {
    private val models = ConcurrentHashMap<Int, Pair<String, BlockModelWrapper>>()
    private val itemModels = ConcurrentHashMap<Int, BlockModelWrapper>()
    private val customModelDataToVariation = ConcurrentHashMap<Int, Int>()
    private val blockPositions = ConcurrentHashMap<Long, ConcurrentHashMap<BlockPos, Int>>()
    private val renderers = ConcurrentHashMap<BlockModelWrapper, RendererEntry>()
    private val warnedModels = ConcurrentHashMap.newKeySet<net.minecraft.resources.ResourceLocation>()

    fun register(variation: Int, modelId: String, wrapper: BlockModelWrapper, customModelData: Int = -1) {
        models[variation] = modelId to wrapper
        if (customModelData > 0) {
            itemModels[customModelData] = wrapper
            customModelDataToVariation[customModelData] = variation
        }
        Lantern.logger.info(
            "[Lantern] Registered block model: variation={}, id={}, cmd={}",
            variation,
            modelId,
            customModelData
        )
    }

    fun clear() {
        models.clear()
        itemModels.clear()
        customModelDataToVariation.clear()
        renderers.clear()
        warnedModels.clear()
    }

    fun hasModel(variation: Int): Boolean = models.containsKey(variation)

    fun getWrapper(variation: Int): BlockModelWrapper? = models[variation]?.second

    fun getModelId(variation: Int): String? = models[variation]?.first

    fun getWrapperByCustomModelData(customModelData: Int): BlockModelWrapper? = itemModels[customModelData]

    fun getVariationByCmd(customModelData: Int): Int? = customModelDataToVariation[customModelData]

    fun isCarrierBlock(state: BlockState): Boolean = state.`is`(Blocks.BARREL)

    fun isTrackedPosition(pos: BlockPos): Boolean = getVariation(pos) != null

    fun addBlockPosition(pos: BlockPos, variation: Int) {
        blockPositions.computeIfAbsent(chunkKey(pos)) { ConcurrentHashMap() }[pos.immutable()] = variation
    }

    fun removeBlockPosition(pos: BlockPos) {
        val key = chunkKey(pos)
        blockPositions.computeIfPresent(key) { _, positions ->
            positions.remove(pos)
            positions.takeUnless { it.isEmpty() }
        }
    }

    fun clearPositions() {
        val oldPositions = blockPositions.values.flatMap { it.keys }
        blockPositions.clear()
        if (Minecraft.getInstance().level != null) {
            oldPositions.forEach(::markSectionDirtyAt)
        }
    }

    fun getVariation(pos: BlockPos): Int? = blockPositions[chunkKey(pos)]?.get(pos)

    fun getWrapperByPos(pos: BlockPos): BlockModelWrapper? = getVariation(pos)?.let(::getWrapper)

    fun getPositionCount(): Int = blockPositions.values.sumOf { it.size }

    fun submit(
        wrapper: BlockModelWrapper,
        instanceId: Long,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        cameraState: CameraRenderState,
        packedLight: Int,
        partialTick: Float,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay? = null
    ) {
        val modelId = GeckoResourceIds.model(wrapper.modelLocation)
        if (!GeckoLibResources.getBakedModels().containsKey(modelId)) {
            if (warnedModels.add(modelId)) {
                Lantern.logger.warn("[Lantern] Block model not yet cached, deferring render: {}", modelId)
            }
            return
        }
        val entry = renderers.computeIfAbsent(wrapper) {
            val animatable = BlockAnimatable(wrapper)
            RendererEntry(animatable, BlockGeoRenderer(wrapper))
        }
        entry.renderer.submit(
            poseStack,
            entry.animatable,
            BlockRenderContext(instanceId, crumblingOverlay),
            submitNodes,
            cameraState,
            packedLight,
            partialTick,
            null
        )
    }

    fun markSectionsDirty() {
        val renderer = Minecraft.getInstance().levelRenderer
        blockPositions.values.forEach { positions ->
            positions.keys.forEach { pos ->
                renderer.setSectionDirty(pos.x shr 4, pos.y shr 4, pos.z shr 4)
            }
        }
    }

    fun markSectionDirtyAt(pos: BlockPos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(pos.x shr 4, pos.y shr 4, pos.z shr 4)
    }

    private fun chunkKey(pos: BlockPos): Long =
        ((pos.x shr 4).toLong() shl 32) or ((pos.z shr 4).toLong() and 0xFFFFFFFFL)

    private data class RendererEntry(
        val animatable: BlockAnimatable,
        val renderer: BlockGeoRenderer
    )
}
