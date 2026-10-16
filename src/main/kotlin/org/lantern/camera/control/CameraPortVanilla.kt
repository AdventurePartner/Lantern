package org.lantern.camera.control

import java.util.UUID
import net.minecraft.client.Minecraft
import org.lantern.core.camera.CameraPort
import org.lantern.core.camera.Position
import org.lantern.platform.EntityLookupBridge

/** CameraPort 的 1.21.1 / 1.20.1 实现：实体眼位查询、开镜检测、玩家朝向写回 */
object CameraPortVanilla : CameraPort {

    override fun eyePosition(uuid: UUID, partialTick: Float): Position? {
        val entity = EntityLookupBridge.byUuid(uuid) ?: return null
        val eye = entity.getEyePosition(partialTick)
        return Position(eye.x, eye.y, eye.z)
    }

    override fun isScoping(): Boolean =
        Minecraft.getInstance().player?.isScoping == true

    override fun writePlayerOrientation(yaw: Float, pitch: Float) {
        Minecraft.getInstance().player?.let {
            it.yRot = yaw
            it.xRot = pitch
        }
    }
}
