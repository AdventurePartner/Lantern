package org.lantern.camera.control

/**
 * 本帧演出翻滚角（度），由 CameraMixin 在 Camera.setup 尾部写入。
 * Forge 在 setup 之后按 ViewportEvent.ComputeCameraAngles 的 yaw/pitch/roll 重建视图，
 * 直接写进相机的翻滚会被覆盖，所以 Forge 的监听器从这里读出来加到事件上
 */
object CameraRoll {
    @JvmField
    var current: Float = 0f
}
