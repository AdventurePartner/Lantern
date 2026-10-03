package org.lantern.core.image

/**
 * 世界图片动画内核的纯数据：缓动、关键帧、动画规格与采样姿态。
 *
 * 轨道名与协议 JSON 字段一致：offset-x / offset-y / offset-z / scale / alpha / rot-yaw / rot-pitch。
 * 一条轨道是按时间升序的关键帧序列；区间插值的缓动取**目标帧**的 ease
 * （"以什么方式到达这一帧"），首帧之前、末帧之后钳制到端点值。
 * 求值入口在 [ImageAnims]。
 */

/** quad 族缓动：作用于区间归一化进度 t∈[0,1]。 */
enum class ImageEase {
    LINEAR, IN, OUT, IN_OUT;

    fun apply(t: Double): Double = when (this) {
        LINEAR -> t
        IN -> t * t
        OUT -> 1.0 - (1.0 - t) * (1.0 - t)
        IN_OUT -> if (t < 0.5) 2.0 * t * t else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
    }

    companion object {
        fun fromName(name: String?): ImageEase? = when (name?.lowercase()) {
            "linear" -> LINEAR
            "in" -> IN
            "out" -> OUT
            "in_out", "inout" -> IN_OUT
            else -> null
        }
    }
}

data class ImageKeyframe(val time: Int, val value: Double, val ease: ImageEase)

/**
 * 一段世界图片动画。duration 单位 tick（>=1）；loop=true 时时间轴取模循环，
 * 否则播完即终（实例删除判定用 [ImageAnims.finished]）。
 */
class ImageAnimSpec(
    val duration: Int,
    val loop: Boolean,
    val tracks: Map<String, List<ImageKeyframe>>
)

/** 某一时刻采样出的姿态增量：offset 叠加在实例 baseOffset 上，scale 乘在实例 scale 上。 */
class ImagePose(
    var offsetX: Double = 0.0,
    var offsetY: Double = 0.0,
    var offsetZ: Double = 0.0,
    var scale: Double = 1.0,
    var alpha: Double = 1.0,
    var rotYaw: Double = 0.0,
    var rotPitch: Double = 0.0
)
