package org.lantern.internal.handler

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import org.lantern.action.PlayerActionStore
import org.lantern.animation.AnimationHost
import org.lantern.core.anim.control.AnimationControlStore
import org.lantern.core.anim.molang.MolangVariableStore
import org.lantern.core.bind.BindStore
import org.lantern.core.camera.CameraControl
import org.lantern.core.camera.ShoulderCameraState
import org.lantern.core.input.InputLockStore
import org.lantern.costume.handler.CostumeHandler
import org.lantern.internal.chat.ChatChannelHandler
import org.lantern.internal.chat.ChatStyleHandler
import org.lantern.internal.parser.UiParser
import org.lantern.model.handler.AnimationControlHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.uix.input.FocusManager
import org.lantern.worldimage.WorldImageManager

/**
 * 断线与实体离场清理里与平台无关的部分，四个客户端共用：随服务端会话下发的状态
 * 一律不跨服残留。收包解码器、按键轮询状态等平台各自持有的东西由调用方另行清理。
 * 这里引用的 AnimationHost / PlayerActionStore / RendererHandler 等在 NeoForge 上是
 * 同名同签名的副本
 */
object SessionCleanup {

    fun onDisconnect() {
        ChatChannelHandler.clear()
        ChatStyleHandler.clear()
        ResourceHandler.clearSession()
        TextureHandler.reload()
        UiParser.resetAll()
        FocusManager.blur()
        WorldImageManager.clear()
        // 相机演出（lock/shake/fov/offset/path）与越肩参数都是服务端会话态
        CameraControl.hardReset()
        ShoulderCameraState.clear()
        InputLockStore.clear()
        // 动作定义随会话作废：留着的话旧服的翻滚键和连招在新服首包到达前还在生效
        PlayerActionStore.clearDash()
        PlayerActionStore.clear()
        AnimationControlHandler.clearPending()
        MolangVariableStore.clear()
        // 绑定不跨服，否则新服的实体会带着旧绑定渲染
        BindStore.clear()
    }

    /** 实体离开客户端世界（消失、走出视距、换维度）：淘汰按实体缓存的渲染与动画状态 */
    fun onEntityLeave(entity: Entity) {
        val uuid = entity.uuid
        // 本地玩家离场（换维度/重生）：进行中的翻滚位移不能带到新实体上继续推
        if (entity === Minecraft.getInstance().player) {
            PlayerActionStore.clearDash()
        }
        CostumeHandler.evictRenderers(uuid)
        RendererHandler.evict(uuid)
        AnimationHost.remove(uuid)
        BindStore.remove(uuid)
        AnimationControlStore.stop(uuid, null)
        MolangVariableStore.remove(uuid)
    }
}
