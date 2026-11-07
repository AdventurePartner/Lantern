package org.lantern.uix.util

import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule

/**
 * 多行文字（text、button、tooltip 共用）：`\n` 是硬换行，配置里的 `|` 块和列表写法到客户端都是 `\n`。
 * 每个硬换行段的格式互不影响，`§` 颜色不跨行延续（同物品 lore）；按宽度折出来的行延续所在段的格式。
 */
object TextLines {

    private val LINE_BREAK = Regex("\r?\n")

    fun split(text: String): List<String> = text.split(LINE_BREAK)

    /** tooltip 的各行。 */
    fun components(text: String): List<Component> = split(text).map { Component.literal(it) }

    /** text 的折行宽度：写了 width 且没写 `white-space: nowrap` 时按 width 折行，否则为 0（只认硬换行）。 */
    fun wrapWidth(style: StyleRule): Int {
        if (style.getString(StyleProperty.WHITE_SPACE) == "nowrap") return 0
        return style.getInt(StyleProperty.WIDTH).coerceAtLeast(0)
    }

    /** 没有硬换行、也不用折行：调用方照旧按字符串画一行，单行文字的绘制与以前完全一致。 */
    fun isSingleLine(font: Font, text: String, wrapWidth: Int): Boolean =
        text.indexOf('\n') < 0 && (wrapWidth <= 0 || font.width(text) <= wrapWidth)

    /** 按硬换行拆段，[wrapWidth] > 0 时每段再按宽度折行；空段保留为空行。 */
    fun layout(font: Font, text: String, wrapWidth: Int): List<FormattedCharSequence> =
        split(text).flatMap { line ->
            when {
                line.isEmpty() -> listOf(FormattedCharSequence.EMPTY)
                wrapWidth > 0 -> font.split(Component.literal(line), wrapWidth).ifEmpty { listOf(FormattedCharSequence.EMPTY) }
                else -> listOf(Component.literal(line).visualOrderText)
            }
        }

    fun width(font: Font, lines: List<FormattedCharSequence>): Int = lines.maxOfOrNull { font.width(it) } ?: 0
}
