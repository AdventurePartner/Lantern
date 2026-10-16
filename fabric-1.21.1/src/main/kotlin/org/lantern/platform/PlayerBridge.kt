package org.lantern.platform

import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.ResourceLocation

object PlayerBridge {
    /** 玩家当前皮肤贴图（hostDriven 外观的 geo UV 即标准 64x64 皮肤布局） */
    @JvmStatic
    fun skinTexture(player: AbstractClientPlayer): ResourceLocation = player.skin.texture()
}
