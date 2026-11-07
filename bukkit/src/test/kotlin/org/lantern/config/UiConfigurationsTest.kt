package org.lantern.config

import com.google.gson.JsonObject
import org.bukkit.configuration.file.YamlConfiguration
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * screens/huds yml 里 text / tooltip 的多行写法：列表与 `|` 块都要变成带 `\n` 的字符串下发，
 * 单行字符串原样保留。用真实 YamlConfiguration 加载，覆盖根节点（ConfigurationSection）
 * 与 children（原始 Map）两条解析路径。
 */
class UiConfigurationsTest {

    private val yaml = """
        id: multiline
        screen-type: gui
        root:
          type: text
          text:
            - "第一行"
            - "第二行"
          tooltip: "单行提示"
          children:
            - type: text
              text: |-
                块第一行
                块第二行
              tooltip:
                - "§e标题"
                - ""
                - "说明"
            - type: button
              text: "确认"
              tooltip: |-
                按下后
                关闭界面
    """.trimIndent()

    private fun parse(): JsonObject {
        val config = YamlConfiguration.loadConfiguration(StringReader(yaml))
        return UiConfigurations.parseScreen(config)
    }

    @Test
    fun `root section list text joins lines`() {
        val root = parse().getAsJsonObject("root")
        assertEquals("第一行\n第二行", root.get("text").asString)
        assertEquals("单行提示", root.get("tooltip").asString)
    }

    @Test
    fun `child block text and list tooltip become multi-line strings`() {
        val children = parse().getAsJsonObject("root").getAsJsonArray("children")
        val text = children[0].asJsonObject
        assertEquals("块第一行\n块第二行", text.get("text").asString)
        assertEquals("§e标题\n\n说明", text.get("tooltip").asString)

        val button = children[1].asJsonObject
        assertEquals("确认", button.get("text").asString)
        assertEquals("按下后\n关闭界面", button.get("tooltip").asString)
    }

    @Test
    fun `text value keeps scalars and joins lists`() {
        assertEquals(null, UiConfigurations.textValue(null))
        assertEquals("42", UiConfigurations.textValue(42))
        assertEquals("a\n\nc", UiConfigurations.textValue(listOf("a", null, "c")))
    }
}
