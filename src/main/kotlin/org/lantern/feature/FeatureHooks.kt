package org.lantern.feature

import net.minecraft.client.Minecraft
import org.lantern.camera.control.CameraPortVanilla
import org.lantern.core.camera.CameraControl
import org.lantern.core.input.InputLockStore
import org.lantern.internal.network.NetworkParser

/**
 * Fabric 1.21.1 / Forge 1.21.1 / Forge 1.20.1 共用的可选能力注册：把 NetworkParser 的
 * 平台钩子接到 common-core 的状态机上。NeoForge 1.21.10 在 LanternNeoForge 里自己接
 * （本包被它排除），两边接的是同一批 common-core 对象
 */
object FeatureHooks {

    fun register() {
        // packet 18 相机（shoulder 由 NetworkParser 直写 ShoulderCameraState，其余演出指令到这里）
        CameraControl.port = CameraPortVanilla
        NetworkParser.cameraActionHandler = CameraControl::handle
        // packet 21 输入锁：写表必须与并集缓存重算同线程，收包线程非主线程时调度过去
        NetworkParser.inputLockHandler = { obj ->
            val client = Minecraft.getInstance()
            if (client.isSameThread) {
                InputLockStore.handle(obj)
            } else {
                client.execute { InputLockStore.handle(obj) }
            }
        }
    }
}
