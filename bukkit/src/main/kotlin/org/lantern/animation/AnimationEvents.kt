package org.lantern.animation

import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 动画开始事件。服务端发出播放指令时触发（对应当前实体的新动画）。
 */
class LanternAnimationStartEvent(
    val animEntity: Entity,
    val animationName: String
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}

/**
 * 动画完成事件。客户端上报 once 动画播完时触发。
 */
class LanternAnimationFinishEvent(
    val animEntity: Entity,
    val animationName: String
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}

/**
 * 动画打断事件。新动画覆盖旧动画、或被明确停止时触发。
 */
class LanternAnimationInterruptEvent(
    val animEntity: Entity,
    val animationName: String
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
