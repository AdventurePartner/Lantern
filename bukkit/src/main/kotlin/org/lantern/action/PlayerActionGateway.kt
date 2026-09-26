package org.lantern.action

import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 玩家主动动作的服务端闸门（附属扩展点）。
 *
 * 默认路线：packet 19 下发的动作条目在客户端本地触发、本地播放，零往返延迟。
 * 这是动作玩法的默认形态，不因为加了扩展点就改变。
 *
 * 需要服务端把关的条目（扣蓝、扣耐力、查冷却、查状态）配 `server-checked: true`：
 * 客户端不再本地播放，改用现有的 C2S 动画事件通道上报一条 `request:<方向>`，
 * 服务端抛 [LanternPlayerActionRequestEvent]，附属监听后决定放不放。
 * 没有任何监听者时事件不会被取消，服务端照常把动作播出去——
 * 也就是说配了 server-checked 但没装附属，动作依然能用，只是多一个来回的延迟。
 * 这个延迟是该模式的固有代价，由配置者自己权衡
 */
object PlayerActionGateway {

    /**
     * 附属强制标记为服务端把关的动作 id。
     *
     * 存在这里而不是写回 PlayerActionCache：配置每次 reload 都会重建一遍 cache 对象，
     * 写在 cache 上的标记会被重载抹掉。附属在 reload 前调用本方法，
     * 下一次下发定义时就会带上 server-checked
     */
    private val forced = ConcurrentHashMap.newKeySet<String>()

    /** 附属入口：把某条动作改成服务端把关。对 playerActions.yml 已写 true 的条目是幂等的 */
    fun markServerChecked(actionId: String) {
        forced.add(actionId)
    }

    /** 撤销标记，回到配置里写的值 */
    fun unmarkServerChecked(actionId: String) {
        forced.remove(actionId)
    }

    fun forcedIds(): Set<String> = forced.toSet()

    /** 下发时的实际取值：配置写了 true，或附属标记过 */
    fun isServerChecked(actionId: String, configured: Boolean): Boolean =
        configured || actionId in forced
}

/**
 * 玩家请求触发一条 server-checked 动作。取消即拒绝，动作不会播出。
 *
 * 能量、耐力、冷却这类资源逻辑由附属在这个事件里实现——Lantern 本身不管资源，
 * 它只提供「玩家想出这一招」这个信号和「准不准」这个开关
 */
class LanternPlayerActionRequestEvent(
    val player: Player,
    /** playerActions.yml 的条目名 */
    val actionId: String,
    /** 客户端按移动键组合判定出的方向：forward / backward / left / right / 四个斜向 / none */
    val direction: String
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
