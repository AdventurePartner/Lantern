package org.lantern.core.anim.clip

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.lantern.core.anim.molang.MolangCompiler
import org.lantern.core.anim.molang.MolangExpression
import org.lantern.core.anim.molang.MolangScope

/** 假求值器：数值关键帧按常量求值，表达式字符串求值为固定值（解析结构不依赖求值语义） */
private object FakeCompiler : MolangCompiler {
    override fun compile(element: com.google.gson.JsonElement): MolangExpression =
        MolangExpression { _ -> element.asDouble }

    override fun compileMolang(source: String): MolangExpression =
        MolangExpression { _ -> 0.0 }
}

private val scope = object : MolangScope {
    override val queries: Map<String, Double> = emptyMap()
    override val variables: Map<String, Double> = emptyMap()
    override val revision: Long = 0L
}

class BedrockAnimationParserTest {

    @Test
    fun `常量轨道解析为单关键帧`() {
        val json = JsonParser.parseString("""
            {"animations": {"idle": {"animation_length": 1.5,
              "bones": {"body": {"rotation": [10, 20, 30]}}}}}
        """).asJsonObject
        val clips = BedrockAnimationParser.parse(json, FakeCompiler)
        val clip = clips["idle"]
        assertNotNull(clip)
        assertEquals(1.5f, clip.length)
        val tracks = clip.bones["body"]
        assertNotNull(tracks)
        assertEquals(1, tracks.rotation.size)
        val v = tracks.rotation[0].value.eval(scope)
        assertEquals(10f, v.x)
        assertEquals(20f, v.y)
        assertEquals(30f, v.z)
        // 位移/缩放未配置 = 空轨道（采样记 NaN 回落静态值）
        assertTrue(tracks.position.isEmpty())
    }

    @Test
    fun `骨骼名大小写归一`() {
        val json = JsonParser.parseString("""
            {"animations": {"a": {"animation_length": 1.0,
              "bones": {"RightArm": {"rotation": [1, 2, 3]}}}}}
        """).asJsonObject
        val clips = BedrockAnimationParser.parse(json, FakeCompiler)
        assertNotNull(clips["a"]!!.bones["rightarm"])
    }

    @Test
    fun `关键帧轨道按时间排序并携带 pre 与 lerp_mode`() {
        val json = JsonParser.parseString("""
            {"animations": {"a": {"animation_length": 2.0, "bones": {"body": {
              "rotation": {
                "0.0": {"post": [0, 0, 0]},
                "1.0": {"pre": [5, 0, 0], "post": [10, 0, 0], "lerp_mode": "catmullrom"}
              }}}}}}
        """).asJsonObject
        val frames = BedrockAnimationParser.parse(json, FakeCompiler)["a"]!!.bones["body"]!!.rotation
        assertEquals(2, frames.size)
        assertEquals(0f, frames[0].time)
        assertEquals(1f, frames[1].time)
        assertNull(frames[0].pre)
        assertNotNull(frames[1].pre)
        assertEquals(5f, frames[1].pre!!.eval(scope).x)
        assertEquals("catmullrom", frames[1].lerpMode)
    }

    @Test
    fun `loop 语义解析`() {
        fun parseLoop(loopRaw: String?): ClipData {
            val loopNode = loopRaw?.let { ", \"loop\": $it" } ?: ""
            val json = JsonParser.parseString("""
                {"animations": {"a": {"animation_length": 1.0 $loopNode,
                  "bones": {"body": {"rotation": [0, 0, 0]}}}}}
            """).asJsonObject
            return BedrockAnimationParser.parse(json, FakeCompiler)["a"]!!
        }
        // 缺省 loop = 循环；true = 循环；false = 播一遍；"hold_on_last_frame" = 停末帧
        assertTrue(parseLoop(null).loops)
        assertTrue(parseLoop("true").loops)
        assertTrue(!parseLoop("false").loops)
        assertTrue(!parseLoop("\"hold_on_last_frame\"").loops)
        assertTrue(parseLoop("\"hold_on_last_frame\"").holdsLastFrame)
    }

    @Test
    fun `缺 animation_length 的条目被跳过`() {
        val json = JsonParser.parseString("""
            {"animations": {"bad": {"bones": {"body": {"rotation": [0, 0, 0]}}}}}
        """).asJsonObject
        assertNull(BedrockAnimationParser.parse(json, FakeCompiler)["bad"])
    }
}
