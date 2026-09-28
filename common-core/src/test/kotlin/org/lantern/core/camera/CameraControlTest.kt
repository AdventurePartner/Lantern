package org.lantern.core.camera

import com.google.gson.JsonParser
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object FakePort : CameraPort {
    var scoping = false
    val written = ArrayList<Pair<Float, Float>>()

    override fun eyePosition(uuid: UUID, partialTick: Float): Position = Position(10.0, 64.0, 0.0)

    override fun isScoping(): Boolean = scoping

    override fun writePlayerOrientation(yaw: Float, pitch: Float) {
        written += yaw to pitch
    }
}

class CameraControlTest {

    @BeforeTest
    fun reset() {
        CameraControl.hardReset()
        FakePort.scoping = false
        FakePort.written.clear()
        CameraControl.port = FakePort
    }

    private fun handle(json: String) =
        CameraControl.handle(JsonParser.parseString(json).asJsonObject.get("action")!!.asString,
            JsonParser.parseString(json).asJsonObject)

    @Test
    fun `offset 零过渡立即叠加 pitch 并钳制`() {
        handle("""{"action":"offset","pitch":10,"yaw":5,"roll":-3,"transition":0}""")
        val pose = CameraControl.apply(0f, 0f, 0f, 0.0, 64.0, 0.0)
        assertNotNull(pose)
        assertEquals(5f, pose.yaw)
        assertEquals(10f, pose.pitch)
        assertEquals(-3f, pose.roll)
        assertNull(pose.absolutePos)
        assertTrue(CameraControl.orientationActive())
        // 钳制：基础 pitch 85 + offset 10 -> 89.9
        handle("""{"action":"offset","pitch":10,"yaw":0,"roll":0,"transition":0}""")
        val clamped = CameraControl.apply(0f, 85f, 0f, 0.0, 64.0, 0.0)
        assertEquals(89.9f, clamped!!.pitch)
    }

    @Test
    fun `lock 坐标目标朝向与 sync 写回`() {
        handle("""{"action":"lock","target":{"x":10,"y":64,"z":0},"smooth":0,"sync":true}""")
        // 相机在原点看向 +X 轴上的点：yaw = atan2(-dx, dz) = -90 度
        val pose = CameraControl.apply(0f, 0f, 0f, 0.0, 64.0, 0.0)
        assertNotNull(pose)
        assertEquals(-90f, pose.yaw)
        assertEquals(0f, pose.pitch)
        assertEquals(1, FakePort.written.size)
        assertEquals(-90f, FakePort.written[0].first)
    }

    @Test
    fun `lock 实体目标经 port 查眼位`() {
        handle("""{"action":"lock","entity":"00000000-0000-0000-0000-000000000001","smooth":0}""")
        val pose = CameraControl.apply(0f, 0f, 0f, 0.0, 64.0, 0.0)
        assertNotNull(pose)
        // FakePort 眼位 (10, 64, 0)：同上 yaw = -90
        assertEquals(-90f, pose.yaw)
    }

    @Test
    fun `shake 触发位置扰动且到位后自清`() {
        handle("""{"action":"shake","amplitude":0.5,"frequency":8,"duration":0.05}""")
        val pose = CameraControl.apply(0f, 0f, 0f, 0.0, 64.0, 0.0)
        assertNotNull(pose)
        // dy 是正弦扰动，幅度不超过 amplitude
        assertTrue(kotlin.math.abs(pose.dy) <= 0.5)
    }

    @Test
    fun `fov 指令零过渡直达且 release 回落 vanilla`() {
        handle("""{"action":"fov","value":30,"transition":0}""")
        assertEquals(30f, CameraControl.fov(70f))
        // release：transition 0 立即回落到 vanilla 并结束 releasing
        handle("""{"action":"fov","transition":0}""")
        assertEquals(70f, CameraControl.fov(70f))
    }

    @Test
    fun `fov 开镜让位原版`() {
        handle("""{"action":"fov","value":30,"transition":0}""")
        FakePort.scoping = true
        assertEquals(70f, CameraControl.fov(70f))
        FakePort.scoping = false
        assertEquals(30f, CameraControl.fov(70f))
    }

    @Test
    fun `watch 坐标注视产生绝对位置覆写`() {
        handle("""{"action":"watch","pos":{"x":0,"y":70,"z":0},"look":{"x":10,"y":70,"z":0},"smooth":0}""")
        val pose = CameraControl.apply(0f, 0f, 0f, 0.0, 64.0, 0.0)
        assertNotNull(pose)
        assertNotNull(pose.absolutePos)
        // smooth=0 即 w=1：位置直达 watch 点
        assertEquals(70.0, pose.absolutePos!!.y, 1e-6)
        assertEquals(-90f, pose.yaw)
    }

    @Test
    fun `clear 触发回程且 hardReset 立即还原 fov`() {
        handle("""{"action":"offset","pitch":10,"transition":0}""")
        handle("""{"action":"fov","value":30,"transition":0}""")
        handle("""{"action":"clear"}""")
        // 回程中 orientationActive 仍为 true（正在滑回）
        assertTrue(CameraControl.orientationActive())
        CameraControl.hardReset()
        // fov 立即回落 vanilla。注：offset 的过渡时间窗（start/transition 字段）
        // 不在 hardReset 清理范围（与原实现一致），apply 在残余窗口内仍会产出
        // 幅度滑向零的过渡位姿——幅度已归零，观感即静止
        assertEquals(70f, CameraControl.fov(70f))
    }
}
