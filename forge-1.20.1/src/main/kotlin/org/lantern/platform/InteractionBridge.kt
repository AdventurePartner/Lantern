package org.lantern.platform

import net.minecraft.world.entity.player.Player

/** 1.20.1 没有 blockInteractionRange，交互距离来自 Forge 的 REACH 属性扩展 */
object InteractionBridge {
    @JvmStatic
    fun blockRange(player: Player): Double = player.blockReach

    @JvmStatic
    fun entityRange(player: Player): Double = player.entityReach
}
