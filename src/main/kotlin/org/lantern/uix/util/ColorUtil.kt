package org.lantern.uix.util

import java.util.concurrent.ConcurrentHashMap

object ColorUtil {
    private val cache = ConcurrentHashMap<String, Int>()

    fun parseColor(hex: String, fallback: Int = 0xFFFFFFFF.toInt()): Int {
        if (hex.isBlank()) return fallback
        return cache.getOrPut(hex) { tryParseColor(hex) ?: fallback }
    }

    /** `#RRGGBB` 视为不透明，`#AARRGGBB` 带透明度；无法解析返回 null。 */
    fun tryParseColor(hex: String): Int? {
        val clean = hex.trim().trimStart('#')
        return when {
            clean.length == 6 -> clean.toIntOrNull(16)?.let { (0xFF shl 24) or it }
            // 按 Long 解析：alpha >= 0x80 的 8 位色值超出 Int 正数范围
            clean.length in 1..8 -> clean.toLongOrNull(16)?.toInt()
            else -> null
        }
    }
}
