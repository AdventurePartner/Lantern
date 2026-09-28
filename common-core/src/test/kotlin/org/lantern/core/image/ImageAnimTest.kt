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

    private fun frame(t: Int, v: Double, ease: String? = null): JsonObject =
        JsonObject().apply {
            addProperty("t", t)
            addProperty("v", v)
            if (ease != null) addProperty("ease", ease)
        }
}
