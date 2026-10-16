package org.lantern.internal.handler

import org.lantern.internal.chat.ChatChannelHandler
import org.lantern.internal.chat.ChatStyleHandler
import org.lantern.internal.parser.UiParser
import org.lantern.uix.input.FocusManager
import org.lantern.worldimage.WorldImageManager

/**
 * 断线清理里与平台无关的部分，四个客户端的断线回调共用：随服务端会话下发的状态
 * 一律不跨服残留。收包解码器、按键轮询状态等平台各自持有的东西由调用方另行清理
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
    }
}
