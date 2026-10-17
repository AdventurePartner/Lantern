package org.lantern.platform

import net.minecraft.client.Minecraft

object TimeBridge {
    /** 本渲染帧的游戏刻插值（暂停时冻结） */
    @JvmStatic
    fun partialTick(): Float = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
}
