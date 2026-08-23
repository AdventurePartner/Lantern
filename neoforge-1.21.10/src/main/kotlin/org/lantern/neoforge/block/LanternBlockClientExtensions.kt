package org.lantern.neoforge.block

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.platform.IdentifierBridge

internal object LanternBlockClientExtensions : IClientBlockExtensions {
    override fun playBreakSound(state: BlockState, level: Level, pos: BlockPos): Boolean {
        val breakSound = BlockRendererHandler.getWrapperByPos(pos)
            ?.breakSound
            ?.takeIf { it.isNotBlank() }
            ?: return false
        level.playLocalSound(
            pos,
            SoundEvent.createVariableRangeEvent(IdentifierBridge.parse(breakSound)),
            SoundSource.BLOCKS,
            1.0f,
            1.0f,
            false
        )
        return true
    }
}
