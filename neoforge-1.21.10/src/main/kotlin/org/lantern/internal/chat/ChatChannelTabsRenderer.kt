package org.lantern.internal.chat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max

object ChatChannelTabsRenderer {
    private const val BUTTON_HEIGHT = 12
    private const val GAP = 2
    private const val START_X = 2
    private const val INPUT_TOP_OFFSET = 28
    private const val MIN_BUTTON_WIDTH = 24
    private const val HORIZONTAL_PADDING = 12
    private const val SELECTED_BACKGROUND = 0xC04A90D9.toInt()
    private const val HOVER_OUTLINE = 0xFFFFFFFF.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val UNREAD_DOT_COLOR = 0xFFFF3333.toInt()

    private var hitboxes: List<Hitbox> = emptyList()

    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val channels = ChatChannelHandler.channels()
        if (channels.isEmpty()) {
            hitboxes = emptyList()
            return
        }

        ChatChannelHandler.markActiveChannelRead()
        val client = Minecraft.getInstance()
        val maxButtonWidth = (graphics.guiWidth() - START_X * 2).coerceAtLeast(MIN_BUTTON_WIDTH)
        val background = client.options.getBackgroundColor(0x80000000.toInt())
        val activeChannelId = ChatChannelHandler.activeChannelId()
        val newHitboxes = ArrayList<Hitbox>(channels.size)
        var x = START_X
        var y = graphics.guiHeight() - INPUT_TOP_OFFSET

        channels.forEach { channel ->
            val width = max(client.font.width(channel.displayName) + HORIZONTAL_PADDING, MIN_BUTTON_WIDTH)
                .coerceAtMost(maxButtonWidth)
            if (x != START_X && x + width > graphics.guiWidth() - START_X) {
                x = START_X
                y -= BUTTON_HEIGHT + GAP
            }

            val hitbox = Hitbox(channel.id, x, y, width, BUTTON_HEIGHT)
            newHitboxes.add(hitbox)
            val selected = channel.id == activeChannelId
            graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, if (selected) SELECTED_BACKGROUND else background)
            if (hitbox.contains(mouseX, mouseY)) {
                graphics.submitOutline(x, y, width, BUTTON_HEIGHT, HOVER_OUTLINE)
            }
            graphics.drawString(client.font, channel.displayName, x + 6, y + 2, TEXT_COLOR, false)
            if (ChatChannelHandler.hasUnreadChannel(channel.id)) {
                val dotRight = x + width - 2
                graphics.fill(dotRight - 4, y + 1, dotRight, y + 5, UNREAD_DOT_COLOR)
            }
            x += width + GAP
        }

        hitboxes = newHitboxes
    }

    fun handleClick(mouseX: Int, mouseY: Int): Boolean {
        val hitbox = hitboxes.firstOrNull { it.contains(mouseX, mouseY) } ?: return false
        return ChatChannelHandler.selectChannel(hitbox.channelId)
    }

    private data class Hitbox(
        val channelId: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        fun contains(px: Int, py: Int): Boolean =
            px >= x && py >= y && px < x + width && py < y + height
    }
}
