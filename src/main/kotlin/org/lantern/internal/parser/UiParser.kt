package org.lantern.internal.parser

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.lantern.Lantern
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.SlotLayoutManager
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.style.StyleSheet
import org.lantern.internal.placeholder.PlaceholderStore
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.button.ButtonWidgetImpl
import org.lantern.uix.widget.image.ImageWidgetImpl
import org.lantern.uix.widget.input.InputWidgetImpl
import org.lantern.uix.widget.panel.PanelWidgetImpl
import org.lantern.uix.hud.VanillaHudElement
import org.lantern.uix.widget.slot.HotbarSelectionWidgetImpl
import org.lantern.uix.widget.slot.SlotWidgetImpl
import org.lantern.uix.widget.text.TextWidgetImpl
import org.lantern.uix.properties.impl.ImageProperties
import java.util.EnumSet

/**
 * Parses a JSON "screens" array (as sent by the Bukkit plugin) into IWidget trees
 * and registers them in [UiScreenStorage].
 */
object UiParser {

    /** 清空全部服务端下发的 UI 状态（重新下发前、断线时调用）；原版 HUD 隐藏随之解除。 */
    fun resetAll() {
        PlaceholderStore.clear()
        LayoutCache.clear()
        SlotLayoutManager.resetAll()
        UiScreenStorage.clear()
    }

    fun parseScreens(screensArray: JsonArray) {
        resetAll()
        screensArray.map { it as JsonObject }.forEach { screenObj ->
            val id = screenObj.get("id")?.asString ?: return@forEach
            val screenType = when (screenObj.get("screen-type")?.asString) {
                "gui" -> ScreenType.GUI
                "overlay" -> ScreenType.OVERLAY
                else -> ScreenType.HUD
            }
            val matchTitle = screenObj.get("match-title")?.asString
            val matchScreen = screenObj.get("match-screen")?.asString
            val index = screenObj.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            val cancelVanillaBg = screenObj.get("cancel-vanilla-bg")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            val hideVanilla = parseHideVanilla(id, screenObj.get("hide-vanilla"))
            val styleSheet = screenObj.getAsJsonObject("styles")
                ?.let { StyleSheet.fromJson(it) } ?: StyleSheet.EMPTY
            val rootObj = screenObj.getAsJsonObject("root") ?: return@forEach
            val rootWidget = parseNode(rootObj, styleSheet)
            UiScreenStorage.put(id, rootWidget, screenType, matchTitle, matchScreen, index, cancelVanillaBg, hideVanilla)
        }
    }

    /** `hide-vanilla` 接受字符串数组或单个字符串；未知 id 告警后忽略。 */
    private fun parseHideVanilla(screenId: String, element: JsonElement?): Set<VanillaHudElement> {
        if (element == null || element.isJsonNull) return emptySet()
        val keys = when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            element.isJsonPrimitive -> listOf(element.asString)
            else -> emptyList()
        }
        val result = EnumSet.noneOf(VanillaHudElement::class.java)
        for (key in keys) {
            val parsed = VanillaHudElement.parse(key)
            if (parsed.isEmpty()) {
                Lantern.logger.warn(
                    "[Lantern] 界面 '{}' 的 hide-vanilla 含未知元素 '{}'，可用值: {}, {}",
                    screenId, key, VanillaHudElement.entries.joinToString { it.key }, VanillaHudElement.ALL
                )
            }
            result.addAll(parsed)
        }
        return result
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
            "slot" -> SlotWidgetImpl(source = node.get("source")?.asString)
            "hotbar-selection" -> HotbarSelectionWidgetImpl(
                texture = node.get("texture")?.asString ?: ""
            )
            else -> PanelWidgetImpl()
        }

        widget.style = if (widget is SlotWidgetImpl) withLegacySlotTexture(node, resolvedStyle) else resolvedStyle
        widget.tooltip = node.get("tooltip")?.asString ?: ""

        if (widget is PanelWidgetImpl) {
            // 节点级 index：兄弟节点间升序稳定排序（同值保持书写顺序），不改变所属渲染层
            node.getAsJsonArray("children")
                ?.map { it as JsonObject }
                ?.sortedBy { it.get("index")?.takeIf { p -> p.isJsonPrimitive }?.asInt ?: 0 }
                ?.forEach { childObj ->
                    val child = parseNode(childObj, styleSheet)
                    child.parent = widget
                    widget.children.add(child)
                }
        }

        return widget
    }

    /** slot 旧写法 `texture: none|路径` 等同 `style.background`；两者都写时以 style 为准。 */
    private fun withLegacySlotTexture(node: JsonObject, style: StyleRule): StyleRule {
        val texture = node.get("texture")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        if (texture.isNullOrEmpty() || style.get(StyleProperty.BACKGROUND) != null) return style
        val background = if (texture.equals("none", ignoreCase = true)) "none" else "url($texture)"
        return style.merge(StyleRule(mapOf(StyleProperty.BACKGROUND to background)))
    }
}
