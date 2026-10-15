package org.lantern.internal.chat

object ChatStyleRules {
    const val MIN_WIDTH = 40
    const val MAX_WIDTH = 1000
    const val MIN_HEIGHT = 20
    const val MAX_HEIGHT = 1000
    const val MIN_SCALE = 0.25
    const val MAX_SCALE = 4.0

    private val NONE_VALUES = setOf("none", "transparent", "false")

    fun isNone(raw: String): Boolean = raw.trim().lowercase() in NONE_VALUES

    /** 与客户端 ColorUtil 同口径：`#RRGGBB` 视为不透明，`#AARRGGBB` 带透明度。 */
    fun parseColor(raw: String): Int? {
        val clean = raw.trim().trimStart('#')
        return when (clean.length) {
            6 -> clean.toIntOrNull(16)?.let { (0xFF shl 24) or it }
            8 -> clean.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    /**
     * 原版行背景的透明度已含玩家“文字背景不透明度”与消息淡出，
     * 配置色的透明度与之相乘，这样淡出和玩家的无障碍设置依然生效。
     */
    fun lineBackground(vanilla: Int, configured: Int?): Int {
        if (configured == null) return vanilla
        val alpha = (vanilla ushr 24) * (configured ushr 24) / 255
        return (alpha shl 24) or (configured and 0xFFFFFF)
    }

    /** 原版文字色的透明度承载淡出，只替换 RGB。 */
    fun textColor(vanilla: Int, configured: Int?): Int {
        if (configured == null) return vanilla
        return (vanilla and 0xFF000000.toInt()) or (configured and 0xFFFFFF)
    }

    fun clampWidth(value: Int): Int = value.coerceIn(MIN_WIDTH, MAX_WIDTH)

    fun clampHeight(value: Int): Int = value.coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    fun clampScale(value: Double): Double = value.coerceIn(MIN_SCALE, MAX_SCALE)
}
