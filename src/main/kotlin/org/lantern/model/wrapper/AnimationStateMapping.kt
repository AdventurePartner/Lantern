package org.lantern.model.wrapper

/**
 * 动画状态名称映射类
 * 将实体的动画状态映射到具体的动画名称
 */
class AnimationStateMapping(
    val idle: String = "idle",
    val walk: String = "walk",
    val attack: String? = null,
    val hurt: String? = null,
    val death: String? = null
) {
    companion object {
        /**
         * 创建默认的动画状态映射（只使用 idle）
         */
        fun default(idleAnimation: String = "idle"): AnimationStateMapping {
            return AnimationStateMapping(idle = idleAnimation, walk = idleAnimation)
        }
    }
}
