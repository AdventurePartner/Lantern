package org.lantern.model.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import org.lantern.Lantern
import org.lantern.model.block.LanternBlockEntity
import org.lantern.model.block.LanternBlockRenderer
import org.lantern.model.wrapper.BlockModelWrapper
import java.util.concurrent.ConcurrentHashMap

/**
 * 自定义方块渲染管理器。
 *
 * 数据流：
 *   pkt 10 → register()  注册 variation→wrapper
 *   pkt 11 → clearPositions() + addBlockPosition() × N + markSectionsDirty()
 *   pkt 12 → addBlockPosition()/removeBlockPosition() + markSectionDirtyAt()
 *
     * 渲染流：
     *   TrackedBarrelRenderer (block entity 渲染阶段) → getOrCreateCachedEntity() + LanternBlockRenderer
     *   BarrelBlockMixin.getRenderShape(open=true) = ENTITYBLOCK_ANIMATED 触发此流程。
     *   BlockRenderDispatcherMixin 在 tracked 位置抑制 vanilla barrel 模型。
 */
object BlockRendererHandler {

    /**
     * 重连后诊断状态跟踪。
     * DISCONNECT 时重置。追踪两个转换：skip → active（数据到达）和首次渲染成功。
     */
    @Volatile
    private var loggedSkip: Boolean = false
    @Volatile
    private var loggedActive: Boolean = false

    fun resetDiagnosticFlags() {
        loggedSkip = false
        loggedActive = false
    }

    /** variation → (modelId, wrapper) */
    private val models = ConcurrentHashMap<Int, Pair<String, BlockModelWrapper>>()

    /** custom_model_data → wrapper（手持物品 GeckoLib 渲染） */
    private val itemModelMap = ConcurrentHashMap<Int, BlockModelWrapper>()

    /** custom_model_data → variation（客户端放置预测时反查） */
    private val cmdToVariation = ConcurrentHashMap<Int, Int>()

    /**
     * 服务端同步的位置 → variation 映射。
     * 外层 key = packChunkPos(cx, cz)，内层 = blockPos → variation。
     */
    private val blockPositions = ConcurrentHashMap<Long, ConcurrentHashMap<BlockPos, Int>>()

    /** 缓存的 LanternBlockEntity，避免每帧重建。按 BlockPos 索引。 */
    private val blockEntityCache = ConcurrentHashMap<BlockPos, LanternBlockEntity>()

    private fun packChunkPos(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    private fun chunkKey(pos: BlockPos): Long =
        packChunkPos(pos.x shr 4, pos.z shr 4)

    // ==================== 模型注册 ====================

    fun hasModel(variation: Int): Boolean = models.containsKey(variation)

    fun getWrapper(variation: Int): BlockModelWrapper? = models[variation]?.second

    fun getModelId(variation: Int): String? = models[variation]?.first

    fun getWrapperByCustomModelData(customModelData: Int): BlockModelWrapper? =
        itemModelMap[customModelData]

    fun getItemModelMapSize(): Int = itemModelMap.size

    fun register(variation: Int, modelId: String, wrapper: BlockModelWrapper, customModelData: Int = -1) {
        models[variation] = modelId to wrapper
        if (customModelData > 0) {
            itemModelMap[customModelData] = wrapper
            cmdToVariation[customModelData] = variation
        }
        Lantern.logger.info("[Lantern] Registered block model: variation={}, id={}, cmd={}, models.size={}, itemModelMap.size={}",
            variation, modelId, customModelData, models.size, itemModelMap.size)
    }

    fun clear() {
        models.clear()
        itemModelMap.clear()
        cmdToVariation.clear()
        blockEntityCache.clear()
    }

    fun getVariationByCmd(customModelData: Int): Int? = cmdToVariation[customModelData]

    // ==================== 载体方块判定 ====================

    fun isCarrierBlock(state: BlockState): Boolean =
        state.`is`(Blocks.BARREL)

    /** 位置是否在 position map 中（即应渲染自定义方块） */
    fun isTrackedPosition(pos: BlockPos): Boolean =
        getVariation(pos) != null

    // ==================== 位置映射 ====================

    fun addBlockPosition(pos: BlockPos, variation: Int) {
        blockPositions.computeIfAbsent(chunkKey(pos)) { ConcurrentHashMap() }[pos] = variation
    }

    fun removeBlockPosition(pos: BlockPos) {
        val key = chunkKey(pos)
        blockPositions.computeIfPresent(key) { _, inner ->
            inner.remove(pos)
            inner.ifEmpty { null }
        }
        blockEntityCache.remove(pos)
    }

    fun clearPositions() {
        blockPositions.clear()
        blockEntityCache.clear()
    }

    fun getVariation(pos: BlockPos): Int? = blockPositions[chunkKey(pos)]?.get(pos)

    fun getPositionCount(): Int = blockPositions.values.sumOf { it.size }


    /** 返回所有 tracked 位置及其 variation，供诊断用 */
    fun getAllTrackedPositions(): List<Pair<BlockPos, Int>> {
        val result = mutableListOf<Pair<BlockPos, Int>>()
        for ((_, inner) in blockPositions) {
            for ((pos, variation) in inner) {
                result.add(pos to variation)
            }
        }
        return result
    }
    fun getWrapperByPos(pos: BlockPos): BlockModelWrapper? {
        val variation = blockPositions[chunkKey(pos)]?.get(pos) ?: return null
        return models[variation]?.second
    }

    // ==================== 缓存管理 ====================

    fun clearCache() {
        blockEntityCache.clear()
    }

    /** 获取或创建缓存的 LanternBlockEntity，供 TrackedBarrelRenderer 复用 */
    fun getOrCreateCachedEntity(pos: BlockPos, wrapper: BlockModelWrapper, level: net.minecraft.world.level.Level): LanternBlockEntity {
        return blockEntityCache.getOrPut(pos) {
            LanternBlockEntity(pos, Blocks.BARREL.defaultBlockState()).also {
                it.wrapper = wrapper
                it.setLevel(level)
            }
        }.also {
            if (it.wrapper !== wrapper) it.wrapper = wrapper
        }
    }

    // ==================== Section 标脏 ====================

    /**
     * 将所有已知位置的 barrel 设置为 open=true，并标记 section 脏。
     * open=true 触发 BarrelModelMixin + BarrelBlockMixin.getRenderShape，
     * 确保 vanilla 和 Sodium 都不渲染桶模型。
     * 通过 level.setBlock() 而非单纯 setSectionDirty()，确保 Sodium 正确重编译 section。
     */
    fun ensureMarkerStateAndDirtySections() {
        val level = Minecraft.getInstance().level ?: return
        var markedCount = 0
        for ((packedKey, inner) in blockPositions) {
            val cx = (packedKey shr 32).toInt()
            val cz = packedKey.toInt()
            if (level.chunkSource.getChunk(cx, cz, false) == null) continue
            for ((pos, _) in inner) {
                val state = level.getBlockState(pos)
                if (state.`is`(Blocks.BARREL) && !state.getValue(BlockStateProperties.OPEN)) {
                    // 设置 open=true，触发 Sodium/vanilla section 重编译
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 0)
                    markedCount++
                } else {
                    // 已经是 open=true 或非 barrel，仅标脏 section
                    Minecraft.getInstance().levelRenderer.setSectionDirty(
                        pos.x shr 4, pos.y shr 4, pos.z shr 4
                    )
                }
            }
        }
        if (markedCount > 0) {
            Lantern.logger.info("[Lantern] Set {} barrel(s) to open=true marker state", markedCount)
        }
    }

    /** 兼容旧调用，委托到 ensureMarkerStateAndDirtySections */
    fun markSectionsDirty() {
        ensureMarkerStateAndDirtySections()
    }

    fun markSectionDirtyAt(pos: BlockPos) {
        val level = Minecraft.getInstance().level
        if (level != null) {
            val state = level.getBlockState(pos)
            if (state.`is`(Blocks.BARREL) && !state.getValue(BlockStateProperties.OPEN)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 0)
            } else {
                Minecraft.getInstance().levelRenderer.setSectionDirty(pos.x shr 4, pos.y shr 4, pos.z shr 4)
            }
        }
    }

    // ==================== 渲染 ====================

    private val lazyRenderer by lazy { LanternBlockRenderer() }

    /**
     * 由 WorldRenderEvents.AFTER_ENTITIES 回调调用。
     * 直接从 position map 渲染自定义方块，绕过 section compiler。
     *
     * 注意：当前已迁移到 TrackedBarrelRenderer（block entity 渲染阶段）。
     * 此方法保留作为备用（无 Iris 场景可用）。
     */
    fun renderCustomBlocks(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        cameraPos: Vec3,
        partialTick: Float
    ) {
        if (blockPositions.isEmpty() || models.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        val renderer = lazyRenderer

        for ((packedKey, inner) in blockPositions) {
            val cx = (packedKey shr 32).toInt()
            val cz = packedKey.toInt()
            if (level.chunkSource.getChunk(cx, cz, false) == null) continue

            for ((pos, variation) in inner) {
                val (_, wrapper) = models[variation] ?: continue

                val be = blockEntityCache.getOrPut(pos) {
                    LanternBlockEntity(pos, Blocks.BARREL.defaultBlockState()).also {
                        it.wrapper = wrapper
                        it.setLevel(level)
                    }
                }
                if (be.wrapper !== wrapper) be.wrapper = wrapper

                val light = sampleNeighborLight(level, pos)
                val renderStack = PoseStack()
                renderStack.last().pose().mul(poseStack.last().pose())
                renderStack.last().normal().mul(poseStack.last().normal())
                renderStack.translate(
                    pos.x.toDouble() - cameraPos.x,
                    pos.y.toDouble() - cameraPos.y,
                    pos.z.toDouble() - cameraPos.z
                )
                try {
                    renderer.render(be, partialTick, renderStack, bufferSource, light, OverlayTexture.NO_OVERLAY)
                } catch (e: Exception) {
                    LanternBlockRenderer.renderMissingBlock(renderStack, bufferSource, light, OverlayTexture.NO_OVERLAY)
                }
            }
        }
    }

    private fun sampleNeighborLight(level: net.minecraft.world.level.Level, pos: BlockPos): Int {
        var maxBlock = 0
        var maxSky = 0
        for (dir in Direction.entries) {
            val neighbor = pos.relative(dir)
            maxBlock = maxOf(maxBlock, level.getBrightness(LightLayer.BLOCK, neighbor))
            maxSky = maxOf(maxSky, level.getBrightness(LightLayer.SKY, neighbor))
        }
        return LightTexture.pack(maxBlock, maxSky)
    }
}
