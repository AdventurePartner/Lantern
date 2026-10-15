package org.lantern.internal.chat

/**
 * 聊天相关的全部外观配置，随 opcode 14 的 `style` 段下发。
 * 默认值即未配置时的外观：窗口沿用原版与玩家设置，按钮/提示沿用改造前的写死值。
 */
data class ChatStyle(
    val window: ChatWindowStyle = ChatWindowStyle(),
    val tabs: ChatTabsStyle = ChatTabsStyle(),
    val notification: ChatNotificationStyle = ChatNotificationStyle()
) {
    companion object {
        val DEFAULT = ChatStyle()
    }
}

/** 原版聊天窗口。可空字段为 null 时跟随原版 / 玩家自己的聊天设置。 */
data class ChatWindowStyle(
    val width: Int? = null,
    val height: Int? = null,
    val heightUnfocused: Int? = null,
    val scale: Double? = null,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    /** 每行背景 ARGB；透明度会再乘上原版算出的透明度（玩家设置 + 淡出）。 */
    val background: Int? = null,
    /** 只取 RGB，透明度沿用原版。 */
    val textColor: Int? = null,
    val inputBackground: Int? = null
) {
    /** 这些字段决定折行宽度与每页行数，变化后要重建原版的显示行。 */
    fun layoutDiffers(other: ChatWindowStyle): Boolean =
        width != other.width || height != other.height ||
            heightUnfocused != other.heightUnfocused || scale != other.scale
}

enum class ChatTextAlign { LEFT, CENTER }

/**
 * 频道按钮。背景字段是原始写法（`#AARRGGBB` / `url(路径)` / `none`），由客户端解析；
 * [background] 为 null 时跟随玩家的聊天背景不透明度。
 */
data class ChatTabsStyle(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val height: Int = 12,
    val gap: Int = 2,
    val minWidth: Int = 24,
    val paddingX: Int = 6,
    val textAlign: ChatTextAlign = ChatTextAlign.LEFT,
    val textShadow: Boolean = false,
    val background: String? = null,
    val selectedBackground: String? = "#C04A90D9",
    /** null = 悬停时背景不变。 */
    val hoverBackground: String? = null,
    /** null = 不画悬停框。 */
    val hoverOutline: Int? = 0xFFFFFFFF.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    /** null = 与 [textColor] 相同。 */
    val selectedTextColor: Int? = null,
    val unreadColor: Int = 0xFFFF3333.toInt(),
    val unreadSize: Int = 4
)

/** 单个频道按钮对 [ChatTabsStyle] 的覆盖，null 字段沿用全局值。 */
data class ChatTabOverride(
    val width: Int? = null,
    val background: String? = null,
    val selectedBackground: String? = null,
    val hoverBackground: String? = null,
    val textColor: Int? = null,
    val selectedTextColor: Int? = null
)

/** 未打开聊天栏时左下角的“收到新消息”提示。 */
data class ChatNotificationStyle(
    val enabled: Boolean = true,
    val text: String = "收到新消息",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textShadow: Boolean = false,
    /** null 时跟随玩家的聊天背景不透明度。 */
    val background: String? = null,
    val paddingX: Int = 4,
    val paddingY: Int = 2,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)
