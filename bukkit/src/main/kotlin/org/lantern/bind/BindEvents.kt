package org.lantern.bind

import java.util.UUID
import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 载体绑定建立事件。[BindRegistry.bind] 成功登记并广播后触发。
 */
class LanternBindEvent(
    val host: Entity,
    val follower: Entity,
    val bind: BindRegistry.Bind
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}

/**
 * 载体解绑事件。手动解绑、到期、宿主或载体消失三种来源都会触发。
 *
 * 这里只给 UUID 不给 Entity：解绑的常见原因恰恰是实体已经不在了，
 * 拿不到 Entity 引用，给个会是 null 的字段不如直接给 UUID
 */
class LanternUnbindEvent(
    val followerUuid: UUID,
    val hostUuid: UUID,
    /** 解绑来源：command / api / expired / gone */
    val reason: String
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
