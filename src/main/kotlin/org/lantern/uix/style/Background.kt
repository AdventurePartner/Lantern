package org.lantern.uix.style

import net.minecraft.client.gui.GuiGraphics
import org.lantern.Lantern
import org.lantern.uix.util.ColorUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一的 `background` 样式值，所有组件共用同一套写法：
 * 不写 = 组件默认背景；`none` / `transparent` / `false` = 不画；
 * `#RRGGBB` / `#AARRGGBB` = 纯色；`url(路径)` = 贴图拉伸填满。
 */
sealed interface Background {
    object None : Background
    data class Color(val argb: Int) : Background
    data class Image(val texture: String) : Background

    /** 组件自带的外观（如 slot 的原版灰框），只作为默认值使用，配置里写不出来。 */
    class Custom(val draw: (GuiGraphics, Int, Int, Int, Int) -> Unit) : Background

    companion object {
        /** value 为 null 表示该字符串无效或为空，回退组件默认背景。 */
        private class Parsed(val value: Background?)

        private val cache = ConcurrentHashMap<String, Parsed>()

        fun resolve(style: StyleRule, default: Background): Background {
            val parsed = when (val raw = style.get(StyleProperty.BACKGROUND)) {
                false -> None
                is String -> cache.getOrPut(raw) { Parsed(parse(raw)) }.value
                null, true -> null
                else -> cache.getOrPut(raw.toString()) { Parsed(parse(raw.toString())) }.value
            }
            return parsed ?: default
        }

        private fun parse(raw: String): Background? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            val lower = value.lowercase()
            if (lower == "none" || lower == "transparent") return None
            if (lower.startsWith("url(") && value.endsWith(")")) {
                val path = value.substring(4, value.length - 1).trim().trim('"', '\'')
                if (path.isNotEmpty()) return Image(path)
            } else if (value.startsWith("#")) {
                ColorUtil.tryParseColor(value)?.let { return Color(it) }
            }
            Lantern.logger.warn(
                "[Lantern] 无法识别的 background 值 '{}'，已使用组件默认背景（可用: none, #RRGGBB, #AARRGGBB, url(路径)）",
                raw
            )
            return null
        }
    }
}
