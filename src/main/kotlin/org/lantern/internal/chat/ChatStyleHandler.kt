package org.lantern.internal.chat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen

/** 当前生效的聊天样式。各平台的 ChatComponent / ChatScreen Mixin 经 @JvmStatic 方法读取。 */
object ChatStyleHandler {
    @Volatile
    var style: ChatStyle = ChatStyle.DEFAULT
        private set

    fun update(newStyle: ChatStyle) {
        val previous = style
        style = newStyle
        if (previous.window.layoutDiffers(newStyle.window)) {
            // 宽度/缩放决定折行，高度决定每页行数：已显示的行要按新尺寸重排
            val client = Minecraft.getInstance()
            client.execute { client.gui.chat.rescaleChat() }
        }
    }

    fun clear() {
        update(ChatStyle.DEFAULT)
    }

    /** 未配置时返回 -1。 */
    @JvmStatic
    fun widthOverride(): Int = style.window.width ?: -1

    /** 打开聊天栏与否用不同高度，与原版 getHeight 的分支一致；未配置时返回 -1。 */
    @JvmStatic
    fun heightOverride(): Int {
        val window = style.window
        val height = if (Minecraft.getInstance().screen is ChatScreen) window.height else window.heightUnfocused
        return height ?: -1
    }

    /** 未配置时返回 -1。 */
    @JvmStatic
    fun scaleOverride(): Double = style.window.scale ?: -1.0

    @JvmStatic
    fun offsetX(): Int = style.window.offsetX

    @JvmStatic
    fun offsetY(): Int = style.window.offsetY

    @JvmStatic
    fun lineBackground(vanilla: Int): Int = ChatStyleRules.lineBackground(vanilla, style.window.background)

    @JvmStatic
    fun textColor(vanilla: Int): Int = ChatStyleRules.textColor(vanilla, style.window.textColor)

    @JvmStatic
    fun inputBackground(vanilla: Int): Int = style.window.inputBackground ?: vanilla
}
