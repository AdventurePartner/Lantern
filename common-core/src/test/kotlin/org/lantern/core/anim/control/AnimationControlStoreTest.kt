package org.lantern.core.anim.control

import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimationControlStoreTest {

    private val uuid = UUID.randomUUID()

    @BeforeTest
    fun reset() {
        AnimationControlStore.reset()
    }

    @Test
    fun `play 与 stop 的基本生命周期`() {
        AnimationControlStore.play(uuid, "skill", 5, loop = false, speed = 1f, expiresAtMs = 1000L)
        val entry = AnimationControlStore.get(uuid)
        assertNotNull(entry)
        assertEquals("skill", entry.animation)
        assertTrue(AnimationControlStore.stop(uuid, "skill"))
        assertNull(AnimationControlStore.get(uuid))
        // stop 不存在的动画返回 false
        assertFalse(AnimationControlStore.stop(uuid, "skill"))
    }

    @Test
    fun `霸体拦截同名重播放行`() {
        AnimationControlStore.play(uuid, "boss_cast", 5, loop = true, speed = 1f,
            expiresAtMs = 0L, uninterruptible = true)
        // 不同名的新 play 被拒
        AnimationControlStore.play(uuid, "other", 5, loop = true, speed = 1f, expiresAtMs = 0L)
        assertEquals("boss_cast", AnimationControlStore.get(uuid)!!.animation)
        // 同名重播放行（服务端刷新 once 到期戳的场景）
        AnimationControlStore.play(uuid, "boss_cast", 5, loop = true, speed = 1f, expiresAtMs = 0L)
        assertEquals("boss_cast", AnimationControlStore.get(uuid)!!.animation)
    }

    @Test
    fun `pause 冻结 resume 补回 once 到期时刻`() {
        val base = System.currentTimeMillis()
        AnimationControlStore.play(uuid, "once_anim", 0, loop = false, speed = 1f,
            expiresAtMs = base + 10_000L)
        AnimationControlStore.pause(uuid)
        assertTrue(AnimationControlStore.isPaused(uuid))
        // 暂停 200ms 后恢复：到期时刻应顺延约 200ms
        Thread.sleep(200)
        AnimationControlStore.resume(uuid)
        assertFalse(AnimationControlStore.isPaused(uuid))
        val shifted = AnimationControlStore.get(uuid)!!.expiresAtMs
        assertTrue(shifted >= base + 10_150L, "resume 应把暂停时长补回到期戳，实际 $shifted")
    }

    @Test
    fun `seek 消费一次即清`() {
        AnimationControlStore.play(uuid, "skill", 5, loop = true, speed = 1f, expiresAtMs = 0L)
        AnimationControlStore.seek(uuid, 1.5f)
        assertEquals(1.5f, AnimationControlStore.consumeSeek(uuid))
        assertNull(AnimationControlStore.consumeSeek(uuid))
    }

    @Test
    fun `stop 清除 pause 与 seek 伴随态`() {
        AnimationControlStore.play(uuid, "a", 5, loop = false, speed = 1f, expiresAtMs = 1000L)
        AnimationControlStore.pause(uuid)
        AnimationControlStore.seek(uuid, 2f)
        AnimationControlStore.stop(uuid, "a")
        // 新 play 不继承上一条的暂停/跳转
        AnimationControlStore.play(uuid, "a", 5, loop = false, speed = 1f, expiresAtMs = 1000L)
        assertFalse(AnimationControlStore.isPaused(uuid))
        assertNull(AnimationControlStore.consumeSeek(uuid))
    }
}
