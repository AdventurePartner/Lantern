package org.lantern.model.renderstate

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 服务端下发的强制动画存储（packet 15）。
 * 每实体最多一条记录，由 AnimationPlayer 逐帧消费。
 *
 * pause/seek 是 forced 条目的伴随状态（暂停当前播控动画 / 跳转其时间轴），
 * 随 forced 的生命周期清理：新 play 或 stop 时不会继承。
 */
object AnimationControlStore {

    private val idCounter = AtomicLong()

    /**
     * @param id 单调递增的指令编号，播放器用它区分「重复播放同一动画」与「正在播的旧指令」
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
    private val paused = ConcurrentHashMap.newKeySet<UUID>()
    private val pendingSeek = ConcurrentHashMap<UUID, Float>()

    fun play(uuid: UUID, animation: String, transition: Int, loop: Boolean, speed: Float, expiresAtMs: Long) {
        forced[uuid] = ForcedAnimation(idCounter.incrementAndGet(), animation, transition, loop, speed, expiresAtMs)
        paused.remove(uuid)
        pendingSeek.remove(uuid)
    }

    /** 停止指定动画；animation 为 null 时清除该实体的全部记录。 */
    fun stop(uuid: UUID, animation: String?): Boolean {
        if (animation == null) {
            paused.remove(uuid)
            pendingSeek.remove(uuid)
            return forced.remove(uuid) != null
        }
        val entry = forced[uuid] ?: return false
        if (entry.animation != animation || !forced.remove(uuid, entry)) return false
        paused.remove(uuid)
        pendingSeek.remove(uuid)
        return true
    }

    fun get(uuid: UUID): ForcedAnimation? = forced[uuid]

    /** 暂停当前播控动画（冻结时间轴与 once 到期）；无播控记录时忽略 */
    fun pause(uuid: UUID) {
        if (forced.containsKey(uuid)) paused.add(uuid)
    }

    fun resume(uuid: UUID) {
        paused.remove(uuid)
    }

    fun isPaused(uuid: UUID): Boolean = paused.contains(uuid)

    /** 跳转当前播控动画的时间轴（秒），由播放器消费一次 */
    fun seek(uuid: UUID, seconds: Float) {
        if (forced.containsKey(uuid)) pendingSeek[uuid] = seconds
    }

    /** 播放器消费待处理的跳转目标（秒）；无则返回 null */
    fun consumeSeek(uuid: UUID): Float? = pendingSeek.remove(uuid)

    fun reset() {
        forced.clear()
        paused.clear()
        pendingSeek.clear()
    }
}
