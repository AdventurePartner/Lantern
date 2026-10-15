package org.lantern.internal.chat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.style.Background

object ChatChannelNotificationRenderer {

    fun render(graphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val style = ChatStyleHandler.style.notification
        if (!style.enabled || !shouldRender(client)) {
            return
        }

        val font = client.font
        val width = font.width(style.text) + style.paddingX * 2
        val height = font.lineHeight + style.paddingY * 2
        val x = style.offsetX
        val y = graphics.guiHeight() - height + style.offsetY
        val background = Background.of(style.background)
            ?: Background.Color(client.options.getBackgroundColor(0x80000000.toInt()))
        BackgroundPainter.paint(graphics, background, x, y, width, height)
        graphics.drawString(font, style.text, x + style.paddingX, y + style.paddingY, style.textColor, style.textShadow)
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
