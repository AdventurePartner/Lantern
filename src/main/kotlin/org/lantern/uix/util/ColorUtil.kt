package org.lantern.uix.util

import java.util.concurrent.ConcurrentHashMap

object ColorUtil {
    private val cache = ConcurrentHashMap<String, Int>()

    fun parseColor(hex: String, fallback: Int = 0xFFFFFFFF.toInt()): Int {
        if (hex.isBlank()) return fallback
        return cache.getOrPut(hex) {
            try {
                val clean = hex.trimStart('#')
                if (clean.length == 6) (0xFF shl 24) or clean.toInt(16) else clean.toInt(16)
            } catch (_: NumberFormatException) {
                fallback
            }
        }
    }
}
