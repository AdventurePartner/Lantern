package org.lantern.platform

import net.minecraft.world.entity.player.Player

object InteractionBridge {
    @JvmStatic
    fun blockRange(player: Player): Double = player.blockInteractionRange()

    @JvmStatic
    fun entityRange(player: Player): Double = player.entityInteractionRange()
}
