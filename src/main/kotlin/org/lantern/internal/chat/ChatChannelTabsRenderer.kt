package org.lantern.internal.chat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.style.Background

object ChatChannelTabsRenderer {
    private const val GLYPH_HEIGHT = 8
    private const val UNREAD_INSET = 1

    private var hitboxes: List<Pair<String, ChatTabRect>> = emptyList()

    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val channels = ChatChannelHandler.channels()
        if (channels.isEmpty()) {
            hitboxes = emptyList()
            return
        }

        ChatChannelHandler.markActiveChannelRead()
        val client = Minecraft.getInstance()
        val font = client.font
        val style = ChatStyleHandler.style.tabs
        val playerBackground = Background.Color(client.options.getBackgroundColor(0x80000000.toInt()))
        val activeChannelId = ChatChannelHandler.activeChannelId()
        val widths = channels.map { ChatTabsLayout.desiredWidth(font.width(it.displayName), style, it.style) }
        val rects = ChatTabsLayout.compute(widths, graphics.guiWidth(), graphics.guiHeight(), style)

        channels.forEachIndexed { index, channel ->
            val rect = rects[index]
            val override = channel.style
            val selected = channel.id == activeChannelId
            val hovered = rect.contains(mouseX, mouseY)

            val background = backgroundFor(style, override, selected, hovered) ?: playerBackground
            BackgroundPainter.paint(graphics, background, rect.x, rect.y, rect.width, rect.height)
            if (hovered) {
                style.hoverOutline?.let { drawOutline(graphics, rect, it) }
            }

            val textWidth = font.width(channel.displayName)
            val textX = when (style.textAlign) {
                ChatTextAlign.LEFT -> rect.x + style.paddingX
                ChatTextAlign.CENTER -> rect.x + (rect.width - textWidth) / 2
            }
            val textY = rect.y + (rect.height - GLYPH_HEIGHT) / 2
            graphics.drawString(font, channel.displayName, textX, textY, textColorFor(style, override, selected), style.textShadow)

            if (style.unreadSize > 0 && ChatChannelHandler.hasUnreadChannel(channel.id)) {
                val right = rect.x + rect.width - 2
                val top = rect.y + UNREAD_INSET
                graphics.fill(right - style.unreadSize, top, right, top + style.unreadSize, style.unreadColor)
            }
        }

        hitboxes = channels.mapIndexed { index, channel -> channel.id to rects[index] }
    }

    fun handleClick(mouseX: Int, mouseY: Int): Boolean {
        val (channelId, _) = hitboxes.firstOrNull { it.second.contains(mouseX, mouseY) } ?: return false
        return ChatChannelHandler.selectChannel(channelId)
    }

    /** 悬停 > 选中 > 普通，逐级回退；单频道覆盖优先于全局。全部未配置时返回 null。 */
    private fun backgroundFor(
        style: ChatTabsStyle,
        override: ChatTabOverride?,
        selected: Boolean,
        hovered: Boolean
    ): Background? {
        if (hovered) {
            Background.of(override?.hoverBackground ?: style.hoverBackground)?.let { return it }
        }
        if (selected) {
            Background.of(override?.selectedBackground ?: style.selectedBackground)?.let { return it }
        }
        return Background.of(override?.background ?: style.background)
    }

    private fun textColorFor(style: ChatTabsStyle, override: ChatTabOverride?, selected: Boolean): Int {
        val normal = override?.textColor ?: style.textColor
        if (!selected) return normal
        return override?.selectedTextColor ?: style.selectedTextColor ?: normal
    }

    private fun drawOutline(graphics: GuiGraphics, rect: ChatTabRect, color: Int) {
        val right = rect.x + rect.width
        val bottom = rect.y + rect.height
        graphics.fill(rect.x, rect.y, right, rect.y + 1, color)
        graphics.fill(rect.x, bottom - 1, right, bottom, color)
        graphics.fill(rect.x, rect.y + 1, rect.x + 1, bottom - 1, color)
        graphics.fill(right - 1, rect.y + 1, right, bottom - 1, color)
    }
}
