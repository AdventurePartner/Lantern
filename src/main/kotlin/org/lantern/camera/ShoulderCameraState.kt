package org.lantern.camera

/**
 * 越肩相机客户端状态。
 *
 * - [enabled]：服务端允许（packet 18 "shoulder" 下发，camera.yml 总开关）
 * - [active]：F5 虚拟视角状态（客户端本地）。越肩是 F5 循环的第四视角：
 *   一人称 → 越肩 → 原版第三人称背后 → 正面 → 一人称；
 *   越肩激活时底层 CameraType 保持 THIRD_PERSON_BACK，渲染/射线/准星按越肩走。
 *
 * 纯 Kotlin 数据对象，不依赖 MC 类：共享 src 可编译到所有客户端平台，
 * 由 [org.lantern.internal.network.NetworkParser] 写入、各平台 Mixin 读写。
 * offsetX > 0 = 相机在玩家右肩（玩家偏屏幕左侧），offsetY > 0 = 抬升，
 * distance = 眼睛到相机的后向距离（格）。
 */
object ShoulderCameraState {

    @JvmField
    @Volatile
    var enabled: Boolean = false

    @JvmField
    @Volatile
    var active: Boolean = false

    @JvmField
    @Volatile
    var offsetX: Float = 0f

    @JvmField
    @Volatile
    var offsetY: Float = 0f

    @JvmField
    @Volatile
    var distance: Float = 4f

    @JvmStatic
    fun update(serverEnabled: Boolean, offsetX: Double, offsetY: Double, distance: Double) {
        this.enabled = serverEnabled
        if (!serverEnabled) {
            this.active = false
        }
        this.offsetX = offsetX.toFloat()
        this.offsetY = offsetY.toFloat()
        this.distance = distance.toFloat()
    }
}
