package org.lantern.model.renderstate

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 服务端下发的强制动画存储（packet 15）。
 * 每实体最多一条记录，由 GenericReplacedEntity 的 custom 控制器逐帧消费。
 */
object AnimationControlStore {

    private val idCounter = AtomicLong()

    /**
     * @param id 单调递增的指令编号，控制器用它区分「重复播放同一动画」与「正在播的旧指令」
     * @param expiresAtMs once 模式的到期时刻（墙钟毫秒）；0 表示不过期（loop 模式，直到 stop）
     */
    data class ForcedAnimation(
        val id: Long,
        val animation: String,
        val transition: Int,
        val loop: Boolean,
        val speed: Float,
        val expiresAtMs: Long
    )

    private val forced = ConcurrentHashMap<UUID, ForcedAnimation>()

    fun play(uuid: UUID, animation: String, transition: Int, loop: Boolean, speed: Float, expiresAtMs: Long) {
        forced[uuid] = ForcedAnimation(idCounter.incrementAndGet(), animation, transition, loop, speed, expiresAtMs)
    }

    /** 停止指定动画；animation 为 null 时清除该实体的全部记录。 */
    fun stop(uuid: UUID, animation: String?): Boolean {
        if (animation == null) return forced.remove(uuid) != null
        val entry = forced[uuid] ?: return false
        return entry.animation == animation && forced.remove(uuid, entry)
    }

    fun get(uuid: UUID): ForcedAnimation? = forced[uuid]

    fun reset() = forced.clear()
}
