package org.lantern.internal.parser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.style.StyleRule
import org.lantern.uix.style.StyleSheet
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.button.ButtonWidgetImpl
import org.lantern.uix.widget.image.ImageWidgetImpl
import org.lantern.uix.widget.input.InputWidgetImpl
import org.lantern.uix.widget.panel.PanelWidgetImpl
import org.lantern.uix.widget.text.TextWidgetImpl
import org.lantern.uix.properties.impl.ImageProperties

/**
 * Parses a JSON "screens" array (as sent by the Bukkit plugin) into IWidget trees
 * and registers them in [UiScreenStorage].
 */
object UiParser {

    fun parseScreens(screensArray: JsonArray) {
        LayoutCache.clear()
        UiScreenStorage.clear()
        screensArray.map { it as JsonObject }.forEach { screenObj ->
            val id = screenObj.get("id")?.asString ?: return@forEach
            val screenType = when (screenObj.get("screen-type")?.asString) {
                "gui" -> ScreenType.GUI
                else -> ScreenType.HUD
            }
            val styleSheet = screenObj.getAsJsonObject("styles")
                ?.let { StyleSheet.fromJson(it) } ?: StyleSheet.EMPTY
            val rootObj = screenObj.getAsJsonObject("root") ?: return@forEach
            val rootWidget = parseNode(rootObj, styleSheet)
            UiScreenStorage.put(id, rootWidget, screenType)
        }
    }

    private fun parseNode(node: JsonObject, styleSheet: StyleSheet): IWidget {
        val type = node.get("type")?.asString ?: "panel"

        val baseStyle = node.get("style-ref")?.asString
            ?.let { styleSheet.get(it) } ?: StyleRule.EMPTY
        val inlineStyle = node.getAsJsonObject("style")
            ?.let { StyleRule.fromJson(it) } ?: StyleRule.EMPTY
        val resolvedStyle = baseStyle.merge(inlineStyle)

        val widget: IWidget = when (type) {
            "text" -> TextWidgetImpl(
                text = node.get("text")?.asString ?: ""
            )
            "button" -> ButtonWidgetImpl(
                text = node.get("text")?.asString ?: "",
                action = node.get("action")?.asString ?: ""
            )
            "input" -> InputWidgetImpl(
                value = node.get("value")?.asString ?: "",
                placeholder = node.get("placeholder")?.asString ?: "",
                maxLength = node.get("max-length")?.asInt ?: 256,
                onChange = node.get("on-change")?.asString ?: ""
            )
            "image" -> ImageWidgetImpl(ImageProperties()).also { w ->
                w.texture = node.get("texture")?.asString ?: ""
            }
            else -> PanelWidgetImpl()
        }

        widget.style = resolvedStyle

        if (widget is PanelWidgetImpl) {
            node.getAsJsonArray("children")?.map { it as JsonObject }?.forEach { childObj ->
                val child = parseNode(childObj, styleSheet)
                child.parent = widget
                widget.children.add(child)
            }
        }

        return widget
    }
}
