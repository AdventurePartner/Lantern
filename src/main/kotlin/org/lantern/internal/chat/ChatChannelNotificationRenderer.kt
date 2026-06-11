package org.lantern.internal.chat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen

object ChatChannelNotificationRenderer {
    private const val MESSAGE = "收到新消息"
    private const val PADDING_X = 4
    private const val PADDING_Y = 2
    private const val X = 0
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    fun render(graphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        if (!shouldRender(client)) {
            return
        }

        val width = client.font.width(MESSAGE) + PADDING_X * 2
        val height = client.font.lineHeight + PADDING_Y * 2
        val y = graphics.guiHeight() - height
        val background = client.options.getBackgroundColor(0x80000000.toInt())
        graphics.fill(X, y, X + width, y + height, background)
        graphics.drawString(client.font, MESSAGE, X + PADDING_X, y + PADDING_Y, TEXT_COLOR, false)
    }

    private fun shouldRender(client: Minecraft): Boolean {
        if (client.player == null || client.level == null) {
            return false
        }
        if (client.options.hideGui) {
            return false
        }
        if (client.screen is ChatScreen) {
            return false
        }
        return ChatChannelHandler.hasUnreadMessages()
    }
}
