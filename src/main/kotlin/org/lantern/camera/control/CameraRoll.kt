package org.lantern.camera.control

/**
 * 本帧演出翻滚角（度），由 Forge 1.20.1 的 Camera1201Mixin 在 Camera.setup 尾部写入。
 * Forge 在 setup 之后按 ViewportEvent.ComputeCameraAngles 的 yaw/pitch/roll 重建视图，
 * 直接写进相机的翻滚会被覆盖，所以 Forge 的监听器从这里读出来加到事件上。
 * Fabric 与 NeoForge 1.21.1 的事件在 setup 之前，共享 CameraMixin 直接写四元数，用不到它
 */
object CameraRoll {
    @JvmField
    var current: Float = 0f
}
