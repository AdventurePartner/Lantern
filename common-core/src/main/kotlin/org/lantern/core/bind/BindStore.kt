package org.lantern.core.bind

import com.google.gson.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.lantern.core.CoreLog

/**
 * 载体绑定表（客户端，packet 20）。
 *
 * 绑定只影响渲染：载体的服务端坐标、碰撞体、移动逻辑一概不动，客户端渲染时
 * 把它画到宿主的**插值位置**上（不是 tick 位置——用 tick 位置每帧都会跳，
 * 宿主跑动时载体会一顿一顿地跟）。
 *
 * 只碰 ConcurrentHashMap，任何线程直接写即可
 */
object BindStore {

    /**
     * @param offsetX/Y/Z rotate=true 时为宿主朝向的局部坐标（x=右 y=上 z=前），
     *                    rotate=false 时为世界坐标偏移
     * @param rotate  载体整体 yaw 跟随宿主身体朝向
     * @param visible 宿主脱离视野时载体仍渲染（跳过视锥剔除）
     * @param expireAtMs 到期解绑的墙钟毫秒；0 = 直到服务端显式解绑
     */
    class Bind(
        val host: UUID,
        val offsetX: Double,
        val offsetY: Double,
        val offsetZ: Double,
        val rotate: Boolean,
        val visible: Boolean,
        val expireAtMs: Long
    )

    private val binds = ConcurrentHashMap<UUID, Bind>()

    /** packet 20：有 host 字段即绑定，没有即解绑 */
    fun handle(obj: JsonObject) {
        val follower = obj.get("follower")?.asString
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        val hostRaw = obj.get("host")?.asString
        if (hostRaw == null) {
            binds.remove(follower)
            CoreLog.logger.log(java.util.logging.Level.INFO, "[Lantern] 解绑载体 {0}", follower)
            return
        }
        val host = runCatching { UUID.fromString(hostRaw) }.getOrNull() ?: return
        val offset = obj.getAsJsonArray("offset")
        val durationMs = obj.get("durationMs")?.asLong ?: 0L
        binds[follower] = Bind(
            host = host,
            offsetX = if (offset != null && offset.size() > 0) offset.get(0).asDouble else 0.0,
            offsetY = if (offset != null && offset.size() > 1) offset.get(1).asDouble else 0.0,
            offsetZ = if (offset != null && offset.size() > 2) offset.get(2).asDouble else 0.0,
            rotate = obj.get("rotate")?.asBoolean ?: true,
            visible = obj.get("visible")?.asBoolean ?: false,
            expireAtMs = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
        )
        // 与服务端那条「绑定建立」配对：两边都打，才能区分"服务端没发"与"客户端没收到"
        CoreLog.logger.log(java.util.logging.Level.INFO, "[Lantern] 收到绑定: 载体={0} 宿主={1}", arrayOf(follower, host))
    }

    /**
     * 取该载体的绑定。到期条目在这里顺手清掉——服务端的到期解绑包可能因为
     * 玩家已走出广播半径而收不到，本地时钟必须能自己收尾
     */
    @JvmStatic
    fun get(follower: UUID): Bind? {
        val bind = binds[follower] ?: return null
        val expire = bind.expireAtMs
        if (expire > 0L && System.currentTimeMillis() >= expire) {
            binds.remove(follower, bind)
            return null
        }
        return bind
    }

    @JvmStatic
    fun remove(follower: UUID) {
        binds.remove(follower)
    }

    @JvmStatic
    fun clear() {
        binds.clear()
    }
}
