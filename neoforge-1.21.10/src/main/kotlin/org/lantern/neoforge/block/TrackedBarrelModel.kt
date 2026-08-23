package org.lantern.neoforge.block

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.data.AtlasIds
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.DynamicBlockStateModel
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.platform.IdentifierBridge

internal class TrackedBarrelModel(
    private val delegate: BlockStateModel
) : DynamicBlockStateModel {
    override fun collectParts(random: RandomSource, parts: MutableList<BlockModelPart>) {
        delegate.collectParts(random, parts)
    }

    override fun collectParts(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        parts: MutableList<BlockModelPart>
    ) {
        if (!BlockRendererHandler.isTrackedPosition(pos)) {
            delegate.collectParts(level, pos, state, random, parts)
        }
    }

    override fun particleIcon(): TextureAtlasSprite = delegate.particleIcon()

    override fun particleIcon(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState
    ): TextureAtlasSprite {
        val wrapper = BlockRendererHandler.getWrapperByPos(pos)
            ?: return delegate.particleIcon(level, pos, state)
        val texture = wrapper.textureLocation
        val spritePath = texture.path.removePrefix("textures/").removeSuffix(".png")
        val spriteId = IdentifierBridge.of(texture.namespace, spritePath)
        return Minecraft.getInstance().atlasManager
            .getAtlasOrThrow(AtlasIds.BLOCKS)
            .getSprite(spriteId)
    }
}
