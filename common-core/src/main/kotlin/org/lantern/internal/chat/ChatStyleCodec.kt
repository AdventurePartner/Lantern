package org.lantern.internal.chat

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.lantern.core.CoreLog

/**
 * opcode 14 中 `style` 段（全局）与频道条目 `style` 段（单频道覆盖）的 JSON -> 样式。
 * bukkit 端从 config.yml 原样组 JSON；这里负责校验：坏值记日志并回退默认，不炸整份配置。
 */
object ChatStyleCodec {
    private val WINDOW_KEYS = setOf(
        "width", "height", "height-unfocused", "scale", "offset-x", "offset-y",
        "background", "text-color", "input-background"
    )
    private val TABS_KEYS = setOf(
        "offset-x", "offset-y", "height", "gap", "min-width", "padding-x", "text-align", "text-shadow",
        "background", "selected-background", "hover-background", "hover-outline",
        "text-color", "selected-text-color", "unread-color", "unread-size"
    )
    private val NOTIFICATION_KEYS = setOf(
        "enabled", "text", "text-color", "text-shadow", "background",
        "padding-x", "padding-y", "offset-x", "offset-y"
    )
    private val OVERRIDE_KEYS = setOf(
        "width", "background", "selected-background", "hover-background", "text-color", "selected-text-color"
    )

    fun parse(obj: JsonObject?): ChatStyle {
        if (obj == null) return ChatStyle.DEFAULT
        warnUnknown(obj, setOf("window", "tabs", "notification"), "chat-style")
        return ChatStyle(
            window = parseWindow(section(obj, "window")),
            tabs = parseTabs(section(obj, "tabs")),
            notification = parseNotification(section(obj, "notification"))
        )
    }

    fun parseTabOverride(obj: JsonObject?, channelId: String): ChatTabOverride? {
        if (obj == null) return null
        val path = "chat-channels.$channelId.style"
        warnUnknown(obj, OVERRIDE_KEYS, path)
        val override = ChatTabOverride(
            width = int(obj, "width", path),
            background = string(obj, "background"),
            selectedBackground = string(obj, "selected-background"),
            hoverBackground = string(obj, "hover-background"),
            textColor = color(obj, "text-color", path),
            selectedTextColor = color(obj, "selected-text-color", path)
        )
        return override.takeIf { it != ChatTabOverride() }
    }

    private fun parseWindow(obj: JsonObject?): ChatWindowStyle {
        if (obj == null) return ChatWindowStyle()
        val path = "chat-style.window"
        warnUnknown(obj, WINDOW_KEYS, path)
        return ChatWindowStyle(
            width = int(obj, "width", path)?.let(ChatStyleRules::clampWidth),
            height = int(obj, "height", path)?.let(ChatStyleRules::clampHeight),
            heightUnfocused = int(obj, "height-unfocused", path)?.let(ChatStyleRules::clampHeight),
            scale = double(obj, "scale", path)?.let(ChatStyleRules::clampScale),
            offsetX = int(obj, "offset-x", path) ?: 0,
            offsetY = int(obj, "offset-y", path) ?: 0,
            background = colorOrNone(obj, "background", path, noneValue = 0),
            textColor = color(obj, "text-color", path),
            inputBackground = colorOrNone(obj, "input-background", path, noneValue = 0)
        )
    }

    private fun parseTabs(obj: JsonObject?): ChatTabsStyle {
        val defaults = ChatTabsStyle()
        if (obj == null) return defaults
        val path = "chat-style.tabs"
        warnUnknown(obj, TABS_KEYS, path)
        return ChatTabsStyle(
            offsetX = int(obj, "offset-x", path) ?: defaults.offsetX,
            offsetY = int(obj, "offset-y", path) ?: defaults.offsetY,
            height = int(obj, "height", path)?.coerceAtLeast(1) ?: defaults.height,
            gap = int(obj, "gap", path)?.coerceAtLeast(0) ?: defaults.gap,
            minWidth = int(obj, "min-width", path)?.coerceAtLeast(1) ?: defaults.minWidth,
            paddingX = int(obj, "padding-x", path)?.coerceAtLeast(0) ?: defaults.paddingX,
            textAlign = align(obj, "text-align", path) ?: defaults.textAlign,
            textShadow = boolean(obj, "text-shadow") ?: defaults.textShadow,
            background = string(obj, "background") ?: defaults.background,
            selectedBackground = string(obj, "selected-background") ?: defaults.selectedBackground,
            hoverBackground = string(obj, "hover-background") ?: defaults.hoverBackground,
            hoverOutline = if (has(obj, "hover-outline")) {
                colorOrNone(obj, "hover-outline", path, noneValue = null, fallback = defaults.hoverOutline)
            } else {
                defaults.hoverOutline
            },
            textColor = color(obj, "text-color", path) ?: defaults.textColor,
            selectedTextColor = color(obj, "selected-text-color", path) ?: defaults.selectedTextColor,
            unreadColor = color(obj, "unread-color", path) ?: defaults.unreadColor,
            unreadSize = int(obj, "unread-size", path)?.coerceAtLeast(0) ?: defaults.unreadSize
        )
    }

    private fun parseNotification(obj: JsonObject?): ChatNotificationStyle {
        val defaults = ChatNotificationStyle()
        if (obj == null) return defaults
        val path = "chat-style.notification"
        warnUnknown(obj, NOTIFICATION_KEYS, path)
        return ChatNotificationStyle(
            enabled = boolean(obj, "enabled") ?: defaults.enabled,
            text = string(obj, "text") ?: defaults.text,
            textColor = color(obj, "text-color", path) ?: defaults.textColor,
            textShadow = boolean(obj, "text-shadow") ?: defaults.textShadow,
            background = string(obj, "background") ?: defaults.background,
            paddingX = int(obj, "padding-x", path)?.coerceAtLeast(0) ?: defaults.paddingX,
            paddingY = int(obj, "padding-y", path)?.coerceAtLeast(0) ?: defaults.paddingY,
            offsetX = int(obj, "offset-x", path) ?: defaults.offsetX,
            offsetY = int(obj, "offset-y", path) ?: defaults.offsetY
        )
    }

    private fun section(obj: JsonObject, key: String): JsonObject? =
        obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun has(obj: JsonObject, key: String): Boolean = primitive(obj, key) != null

    private fun primitive(obj: JsonObject, key: String): JsonElement? =
        obj.get(key)?.takeIf { it.isJsonPrimitive }

    private fun string(obj: JsonObject, key: String): String? =
        primitive(obj, key)?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun boolean(obj: JsonObject, key: String): Boolean? =
        primitive(obj, key)?.asString?.trim()?.lowercase()?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

    private fun double(obj: JsonObject, key: String, path: String): Double? {
        val raw = primitive(obj, key)?.asString?.trim() ?: return null
        return raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: run {
            CoreLog.logger.warning("$path.$key: '$raw' 不是数字，已忽略")
            null
        }
    }

    private fun int(obj: JsonObject, key: String, path: String): Int? =
        double(obj, key, path)?.toInt()

    private fun color(obj: JsonObject, key: String, path: String): Int? {
        val raw = string(obj, key) ?: return null
        return ChatStyleRules.parseColor(raw) ?: run {
            CoreLog.logger.warning("$path.$key: 无法识别的颜色 '$raw'（可用 #RRGGBB / #AARRGGBB），已使用默认值")
            null
        }
    }

    /** 允许写 none 的颜色：none 得到 [noneValue]，未写或写错得到 [fallback]。 */
    private fun colorOrNone(obj: JsonObject, key: String, path: String, noneValue: Int?, fallback: Int? = null): Int? {
        val raw = string(obj, key) ?: return fallback
        if (ChatStyleRules.isNone(raw)) return noneValue
        return color(obj, key, path) ?: fallback
    }

    private fun align(obj: JsonObject, key: String, path: String): ChatTextAlign? {
        val raw = string(obj, key) ?: return null
        return when (raw.lowercase()) {
            "left" -> ChatTextAlign.LEFT
            "center" -> ChatTextAlign.CENTER
            else -> {
                CoreLog.logger.warning("$path.$key: '$raw' 无效（可用 left / center），已使用 left")
                null
            }
        }
    }

    private fun warnUnknown(obj: JsonObject, allowed: Set<String>, path: String) {
        obj.keySet().filter { it !in allowed }.forEach {
            CoreLog.logger.warning("$path: 未知的样式键 '$it'，已忽略（可用: ${allowed.joinToString()}）")
        }
    }
}
