package org.lantern.core.image

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageAnimTest {

    private fun spec(duration: Int, loop: Boolean, vararg tracks: Pair<String, List<ImageKeyframe>>) =
        ImageAnimSpec(duration, loop, mapOf(*tracks))

    @Test
    fun `linear track interpolates between keyframes`() {
        val s = spec(
            10, false,
            ImageAnims.TRACK_OFFSET_Y to listOf(
                ImageKeyframe(0, 0.0, ImageEase.LINEAR),
                ImageKeyframe(10, 2.0, ImageEase.LINEAR)
            )
        )
        assertEquals(0.4, ImageAnims.sample(s, 2.0).offsetY)
        assertEquals(1.0, ImageAnims.sample(s, 5.0).offsetY)
        assertEquals(1.6, ImageAnims.sample(s, 8.0).offsetY)
    }

    @Test
    fun `clamps outside keyframe range`() {
        val s = spec(
            10, false,
            ImageAnims.TRACK_SCALE to listOf(
                ImageKeyframe(2, 0.5, ImageEase.LINEAR),
                ImageKeyframe(6, 1.5, ImageEase.LINEAR)
            )
        )
        // 首帧之前取首帧值，末帧之后取末帧值
        assertEquals(0.5, ImageAnims.sample(s, 0.0).scale)
        assertEquals(0.5, ImageAnims.sample(s, 1.9).scale)
        assertEquals(1.5, ImageAnims.sample(s, 6.0).scale)
        assertEquals(1.5, ImageAnims.sample(s, 9.9).scale)
    }

    @Test
    fun `ease out bends segment toward target`() {
        val s = spec(
            10, false,
            ImageAnims.TRACK_ALPHA to listOf(
                ImageKeyframe(0, 0.0, ImageEase.OUT),
                ImageKeyframe(10, 1.0, ImageEase.OUT)
            )
        )
        // out: 1-(1-t)^2，半程时已走到 0.75
        assertEquals(0.75, ImageAnims.sample(s, 5.0).alpha)
    }

    @Test
    fun `loop wraps timeline`() {
        val s = spec(
            10, true,
            ImageAnims.TRACK_OFFSET_Y to listOf(
                ImageKeyframe(0, 0.0, ImageEase.LINEAR),
                ImageKeyframe(10, 10.0, ImageEase.LINEAR)
            )
        )
        assertEquals(5.0, ImageAnims.sample(s, 15.0).offsetY)
        assertEquals(5.0, ImageAnims.sample(s, 25.0).offsetY)
        assertEquals(0.0, ImageAnims.sample(s, 30.0).offsetY)
    }

    @Test
    fun `finished only for non-loop past duration`() {
        val once = spec(10, false, ImageAnims.TRACK_ALPHA to listOf(ImageKeyframe(0, 1.0, ImageEase.LINEAR)))
        val loop = spec(10, true, ImageAnims.TRACK_ALPHA to listOf(ImageKeyframe(0, 1.0, ImageEase.LINEAR)))

        assertFalse(ImageAnims.finished(once, 9.9))
        assertTrue(ImageAnims.finished(once, 10.0))
        assertFalse(ImageAnims.finished(loop, 100.0))
        assertFalse(ImageAnims.finished(null, 100.0))
    }

    @Test
    fun `null spec samples identity pose`() {
        val pose = ImageAnims.sample(null, 12.5)
        assertEquals(1.0, pose.scale)
        assertEquals(1.0, pose.alpha)
        assertEquals(0.0, pose.offsetY)
    }

    @Test
    fun `codecs parse a full animation`() {
        val track = JsonArray()
        track.add(frame(0, 0.0, "out"))
        track.add(frame(40, 1.5))
        val anim = JsonObject().apply {
            addProperty("duration", 40)
            addProperty("loop", false)
            add("tracks", JsonObject().apply { add("offset-y", track) })
        }

        val parsed = WorldImageCodecs.parseAnimation("my_float", anim)

        assertNotNull(parsed)
        assertEquals(40, parsed.duration)
        assertFalse(parsed.loop)
        assertEquals(1, parsed.tracks.size)
        assertEquals(listOf(ImageKeyframe(0, 0.0, ImageEase.OUT), ImageKeyframe(40, 1.5, ImageEase.LINEAR)),
            parsed.tracks[ImageAnims.TRACK_OFFSET_Y])
    }

    @Test
    fun `codecs reject invalid animations`() {
        // duration 缺失
        assertNull(WorldImageCodecs.parseAnimation("a", JsonObject()))
        // duration 为 0
        assertNull(WorldImageCodecs.parseAnimation("a", JsonObject().apply { addProperty("duration", 0) }))
        // 未知轨道
        assertNull(WorldImageCodecs.parseAnimation("a", JsonObject().apply {
            addProperty("duration", 10)
            add("tracks", JsonObject().apply { add("unknown-track", JsonArray()) })
        }))
        // 帧缺 v
        val badFrame = JsonArray()
        badFrame.add(JsonObject().apply { addProperty("t", 0) })
        assertNull(WorldImageCodecs.parseAnimation("a", JsonObject().apply {
            addProperty("duration", 10)
            add("tracks", JsonObject().apply { add("scale", badFrame) })
        }))
    }

    @Test
    fun `built-ins cover the three named animations`() {
        assertEquals(setOf("damage_pop", "float_up", "drop_spin"), ImageAnims.BUILT_INS.keys)
        assertEquals(20, ImageAnims.BUILT_INS["damage_pop"]!!.duration)
        assertEquals(1.8, ImageAnims.BUILT_INS["drop_spin"]!!.tracks[ImageAnims.TRACK_OFFSET_Y]!!.first().value)
    }

    @Test
    fun `ease 取自目标帧而非源帧`() {
        // 源帧写 in、目标帧写 out：半程值必须是 out 的 0.75 而不是 in 的 0.25
        val s = spec(
            10, false,
            ImageAnims.TRACK_ALPHA to listOf(
                ImageKeyframe(0, 0.0, ImageEase.IN),
                ImageKeyframe(10, 1.0, ImageEase.OUT)
            )
        )
        assertEquals(0.75, ImageAnims.sample(s, 5.0).alpha)
    }

    @Test
    fun `in 与 in_out 缓动数值`() {
        val s = spec(
            10, false,
            ImageAnims.TRACK_SCALE to listOf(
                ImageKeyframe(0, 0.0, ImageEase.LINEAR),
                ImageKeyframe(10, 1.0, ImageEase.IN)
            )
        )
        assertEquals(0.25, ImageAnims.sample(s, 5.0).scale)

        val s2 = spec(
            10, false,
            ImageAnims.TRACK_SCALE to listOf(
                ImageKeyframe(0, 0.0, ImageEase.LINEAR),
                ImageKeyframe(10, 1.0, ImageEase.IN_OUT)
            )
        )
        assertEquals(0.5, ImageAnims.sample(s2, 5.0).scale)
        // in_out 前半是 2t^2：1/4 进度时 0.125
        assertEquals(0.125, ImageAnims.sample(s2, 2.5).scale)
    }

    @Test
    fun `同 t 帧后写者在 t 达到后生效`() {
        val s = spec(
            10, false,
            ImageAnims.TRACK_SCALE to listOf(
                ImageKeyframe(0, 1.0, ImageEase.LINEAR),
                ImageKeyframe(5, 2.0, ImageEase.LINEAR),
                ImageKeyframe(5, 3.0, ImageEase.LINEAR),
                ImageKeyframe(10, 4.0, ImageEase.LINEAR)
            )
        )
        assertEquals(1.98, ImageAnims.sample(s, 4.9).scale)
        // t=5 落在"末帧之后取末值"路径（第二段 t<5 不含等号），取同 t 的后写帧
        assertEquals(3.0, ImageAnims.sample(s, 5.0).scale)
        assertEquals(3.5, ImageAnims.sample(s, 7.5).scale)
    }

    @Test
    fun `duration 为 1 时非循环立即到期`() {
        val s = spec(1, false, ImageAnims.TRACK_SCALE to listOf(ImageKeyframe(0, 1.0, ImageEase.LINEAR)))
        assertTrue(ImageAnims.finished(s, 1.0))
        assertTrue(ImageAnims.finished(s, 0.0).not())
    }

    @Test
    fun `rot 轨道参与采样`() {
        val s = spec(
            30, false,
            ImageAnims.TRACK_ROT_YAW to listOf(
                ImageKeyframe(0, 0.0, ImageEase.OUT),
                ImageKeyframe(30, 720.0, ImageEase.OUT)
            )
        )
        // out 半程 = 75% 行程 = 540 度
        assertEquals(540.0, ImageAnims.sample(s, 15.0).rotYaw)
    }

    @Test
    fun `codecs 解析 loop 动画与乱序帧排序`() {
        val track = JsonArray()
        track.add(frame(40, 1.5))
        track.add(frame(0, 0.0, "out"))
        val anim = JsonObject().apply {
            addProperty("duration", 40)
            addProperty("loop", true)
            add("tracks", JsonObject().apply { add("scale", track) })
        }
        val parsed = WorldImageCodecs.parseAnimation("looper", anim)

        assertNotNull(parsed)
        assertTrue(parsed.loop)
        // 乱序帧被 sortBy 归位后首帧是 t=0
        assertEquals(0, parsed.tracks[ImageAnims.TRACK_SCALE]!!.first().time)
        assertFalse(ImageAnims.finished(parsed, 1000.0))
    }

    @Test
    fun `codecs 对非数组轨道与非对象帧静默跳过，未知 ease 回退 linear`() {
        val frames = JsonArray()
        frames.add(42) // 非对象帧：静默跳过
        frames.add(frame(0, 1.0, "typo_ease")) // 未知 ease：回退 LINEAR
        val anim = JsonObject().apply {
            addProperty("duration", 10)
            add("tracks", JsonObject().apply {
                add("scale", frames)
                add("alpha", JsonObject()) // 轨道值非数组：静默跳过
            })
        }
        val parsed = WorldImageCodecs.parseAnimation("mixed", anim)

        assertNotNull(parsed)
        assertEquals(listOf(ImageKeyframe(0, 1.0, ImageEase.LINEAR)),
            parsed.tracks[ImageAnims.TRACK_SCALE])
        assertEquals(null, parsed.tracks[ImageAnims.TRACK_ALPHA])
    }

    @Test
    fun `bukkit 组包形态可整包解析（数字为 JsonPrimitive Number）`() {
        // 复刻 NetworkHandler.sendWorldImages 的组装形态：yml 数值经 anyToJson
        // 是 JsonPrimitive(Number)，帧是列表内嵌 map 转 JsonObject——整包走 codecs
        val frameList = JsonArray()
        frameList.add(frame(0, 0.0, "out"))
        frameList.add(frame(20, 0.9))
        val animations = JsonObject().apply {
            add("damage_pop_custom", JsonObject().apply {
                addProperty("duration", 20)
                addProperty("loop", false)
                add("tracks", JsonObject().apply { add("offset-y", frameList) })
            })
        }
        val parsed = WorldImageCodecs.parseAnimations(animations)

        assertEquals(1, parsed.size)
        val s = parsed["damage_pop_custom"]!!
        assertEquals(0.0, ImageAnims.sample(s, 0.0).offsetY)
        assertEquals(0.9 * 0.75, ImageAnims.sample(s, 15.0).offsetY, 1e-9)
    }

    private fun frame(t: Int, v: Double, ease: String? = null): JsonObject =
        JsonObject().apply {
            addProperty("t", t)
            addProperty("v", v)
            if (ease != null) addProperty("ease", ease)
        }
}
