package org.lantern.cache

import org.bukkit.configuration.file.YamlConfiguration
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * worldImages.yml 解析链路的纯 JVM 测试：真实 YamlConfiguration（snakeyaml）加载
 * -> WorldImageCache 转 JSON，断言输出形态与客户端 common-core WorldImageCodecs
 * 的输入契约逐字段对齐。不需要起服务器。
 */
class WorldImageCacheTest {

    /** 复刻出厂模板解开注释后的完整配置（含行内 map 关键帧）。 */
    private val template = """
        images:
          soul_ring:
            texture: "image/soul_ring.png"
            size: 1.0
            scale: 1.5
            offset: [0, 2.0, 0]
            facing: fixed
            rot: [90, -15]
            first-person: false
            see-through: true
        animations:
          my_float:
            duration: 40
            loop: false
            tracks:
              offset-y:
                - {t: 0, v: 0, ease: out}
                - {t: 40, v: 1.5}
              alpha:
                - {t: 30, v: 1.0}
                - {t: 40, v: 0.0, ease: in}
        damage:
          enabled: true
          format: "%.1f"
          color: "#55FF55"
          min-damage: 1.0
          merge-ms: 500
          radius: 32
          first-person: true
          scale: 1.2
          size: 0.5
          animation: my_float
    """.trimIndent()

    private fun load(yaml: String = template): WorldImageCache {
        val config = YamlConfiguration.loadConfiguration(StringReader(yaml))
        return WorldImageCache(config)
    }

    @Test
    fun `images 段逐字段转 JSON`() {
        val cache = load()
        val logo = cache.images["soul_ring"]
        assertNotNull(logo)
        assertEquals("image/soul_ring.png", logo.get("texture").asString)
        assertEquals(1.0, logo.get("size").asDouble)
        assertEquals(1.5, logo.get("scale").asDouble)
        assertEquals("fixed", logo.get("facing").asString)
        assertEquals(false, logo.get("first-person").asBoolean)
        assertEquals(true, logo.get("see-through").asBoolean)
        // 列表形态：offset [x,y,z] / rot [yaw,pitch] 保持 JsonArray
        assertEquals(3, logo.getAsJsonArray("offset").size())
        assertEquals(0.0, logo.getAsJsonArray("offset").get(0).asDouble)
        assertEquals(2.0, logo.getAsJsonArray("offset").get(1).asDouble)
        assertEquals(90.0, logo.getAsJsonArray("rot").get(0).asDouble)
        assertEquals(-15.0, logo.getAsJsonArray("rot").get(1).asDouble)
    }

    @Test
    fun `animations 段输出与客户端 codecs 输入契约对齐`() {
        val cache = load()
        val anim = cache.animations.getAsJsonObject("my_float")
        assertNotNull(anim)
        assertEquals(40, anim.get("duration").asInt)
        assertEquals(false, anim.get("loop").asBoolean)
        // 行内 map 关键帧 {-{t,v,ease}} 是列表内嵌 map：snakeyaml 保持 LinkedHashMap，
        // anyToJson 须转成 JsonObject 而不是丢成字符串
        val tracks = anim.getAsJsonObject("tracks")
        val offsetY = tracks.getAsJsonArray("offset-y")
        assertEquals(2, offsetY.size())
        val firstFrame = offsetY.get(0).asJsonObject
        assertEquals(0, firstFrame.get("t").asInt)
        assertEquals(0.0, firstFrame.get("v").asDouble)
        assertEquals("out", firstFrame.get("ease").asString)
        // 无 ease 的帧允许缺失（客户端按 linear 处理）
        val secondFrame = offsetY.get(1).asJsonObject
        assertTrue(secondFrame.get("ease") == null)
        assertEquals(40, secondFrame.get("t").asInt)
    }

    @Test
    fun `damage 段逐项读取`() {
        val damage = load().damage
        assertEquals(true, damage.enabled)
        assertEquals("%.1f", damage.format)
        assertEquals("#55FF55", damage.color)
        assertEquals(1.0, damage.minDamage)
        assertEquals(500L, damage.mergeMs)
        assertEquals(32.0, damage.radius)
        assertEquals(true, damage.firstPerson)
        assertEquals(1.2, damage.scale)
        assertEquals(0.5, damage.size)
        assertEquals("my_float", damage.animation)
    }

    @Test
    fun `非法 format 回退并保持可用`() {
        val cache = load(template.replace("\"%.1f\"", "\"%d\""))
        assertEquals("%.0f", cache.damage.format)
    }

    @Test
    fun `damage 段缺失时用代码默认值`() {
        val cache = load("images: {}\n")
        val damage = cache.damage
        assertEquals(true, damage.enabled)
        assertEquals("%.0f", damage.format)
        assertEquals("#FF5555", damage.color)
        assertEquals(0.5, damage.minDamage)
        assertEquals(250L, damage.mergeMs)
        assertEquals(48.0, damage.radius)
        assertEquals(false, damage.firstPerson)
        assertEquals(0.4, damage.size)
        assertEquals("damage_pop", damage.animation)
    }

    @Test
    fun `空配置不产生模板与动画`() {
        val cache = load("")
        assertTrue(cache.images.isEmpty())
        assertEquals(0, cache.animations.entrySet().size)
    }
}
