package org.lantern.core.image

/**
 * 世界图片动画的求值入口与内置动画表。
 *
 * 时间轴约定：[ticks] 是实例已存活时间（含小数，消费侧用毫秒换算 50ms=tick）。
 * loop 取模循环；非循环钳制在 [0, duration]，到期判定单独走 [finished]。
 */
object ImageAnims {
    const val TRACK_OFFSET_X = "offset-x"
    const val TRACK_OFFSET_Y = "offset-y"
    const val TRACK_OFFSET_Z = "offset-z"
    const val TRACK_SCALE = "scale"
    const val TRACK_ALPHA = "alpha"
    const val TRACK_ROT_YAW = "rot-yaw"
    const val TRACK_ROT_PITCH = "rot-pitch"

    val TRACK_NAMES = setOf(
        TRACK_OFFSET_X, TRACK_OFFSET_Y, TRACK_OFFSET_Z,
        TRACK_SCALE, TRACK_ALPHA, TRACK_ROT_YAW, TRACK_ROT_PITCH
    )

    /**
     * 内置动画（硬编码随内核分发，yml 同名定义覆盖这里）：
     * - damage_pop：伤害数值弹出——先过冲放大再回稳，尾段上浮淡出
     * - float_up：通用上浮淡出
     * - drop_spin：从头顶 +1.8 落到绑定点并自旋两圈（魂环式）
     */
    val BUILT_INS: Map<String, ImageAnimSpec> = mapOf(
        "damage_pop" to ImageAnimSpec(
            20, false, mapOf(
                TRACK_SCALE to listOf(
                    ImageKeyframe(0, 0.5, ImageEase.LINEAR),
                    ImageKeyframe(2, 1.15, ImageEase.OUT),
                    ImageKeyframe(4, 1.0, ImageEase.IN_OUT)
                ),
                TRACK_OFFSET_Y to listOf(
                    ImageKeyframe(0, 0.0, ImageEase.OUT),
                    ImageKeyframe(20, 0.9, ImageEase.OUT)
                ),
                TRACK_ALPHA to listOf(
                    ImageKeyframe(0, 1.0, ImageEase.LINEAR),
                    ImageKeyframe(15, 1.0, ImageEase.LINEAR),
                    ImageKeyframe(20, 0.0, ImageEase.LINEAR)
                )
            )
        ),
        "float_up" to ImageAnimSpec(
            40, false, mapOf(
                TRACK_OFFSET_Y to listOf(
                    ImageKeyframe(0, 0.0, ImageEase.OUT),
                    ImageKeyframe(40, 1.5, ImageEase.OUT)
                ),
                TRACK_ALPHA to listOf(
                    ImageKeyframe(0, 1.0, ImageEase.LINEAR),
                    ImageKeyframe(30, 1.0, ImageEase.LINEAR),
                    ImageKeyframe(40, 0.0, ImageEase.IN)
                )
            )
        ),
        "drop_spin" to ImageAnimSpec(
            30, false, mapOf(
                TRACK_OFFSET_Y to listOf(
                    ImageKeyframe(0, 1.8, ImageEase.OUT),
                    ImageKeyframe(30, 0.0, ImageEase.OUT)
                ),
                TRACK_ROT_YAW to listOf(
                    ImageKeyframe(0, 0.0, ImageEase.OUT),
                    ImageKeyframe(30, 720.0, ImageEase.OUT)
                )
            )
        )
    )

    /** spec 为 null = 实例不带动画，返回恒等姿态（scale/alpha 为 1）。 */
    fun sample(spec: ImageAnimSpec?, ticks: Double): ImagePose {
        val pose = ImagePose()
        if (spec == null) return pose
        val t = timeline(spec, ticks)
        spec.tracks[TRACK_OFFSET_X]?.let { pose.offsetX = sampleTrack(it, t) }
        spec.tracks[TRACK_OFFSET_Y]?.let { pose.offsetY = sampleTrack(it, t) }
        spec.tracks[TRACK_OFFSET_Z]?.let { pose.offsetZ = sampleTrack(it, t) }
        spec.tracks[TRACK_SCALE]?.let { pose.scale = sampleTrack(it, t) }
        spec.tracks[TRACK_ALPHA]?.let { pose.alpha = sampleTrack(it, t) }
        spec.tracks[TRACK_ROT_YAW]?.let { pose.rotYaw = sampleTrack(it, t) }
        spec.tracks[TRACK_ROT_PITCH]?.let { pose.rotPitch = sampleTrack(it, t) }
        return pose
    }

    /** 非循环动画是否已播完（实例到期删除的判定之一）；循环动画恒 false。 */
    fun finished(spec: ImageAnimSpec?, ticks: Double): Boolean =
        spec != null && !spec.loop && ticks >= spec.duration

    private fun timeline(spec: ImageAnimSpec, ticks: Double): Double =
        if (spec.loop) {
            if (spec.duration <= 0) 0.0 else ticks % spec.duration
        } else {
            ticks.coerceIn(0.0, spec.duration.toDouble())
        }

    /** 首帧之前取首帧值、末帧之后取末帧值；区间线性进度经目标帧 ease 弯折。 */
    private fun sampleTrack(frames: List<ImageKeyframe>, t: Double): Double {
        val first = frames.first()
        if (t <= first.time) return first.value
        for (i in 0 until frames.size - 1) {
            val from = frames[i]
            val to = frames[i + 1]
            if (t < to.time) {
                val span = (to.time - from.time).toDouble()
                if (span <= 0.0) return from.value
                val progress = (t - from.time) / span
                return from.value + (to.value - from.value) * to.ease.apply(progress)
            }
        }
        return frames.last().value
    }
}
