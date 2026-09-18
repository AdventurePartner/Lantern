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
     * @param uninterruptible 霸体：播放期间拒绝新的 play 顶替（stop 仍可显式停止）
     */
    data class ForcedAnimation(
        val id: Long,
        val animation: String,
        val transition: Int,
        val loop: Boolean,
        val speed: Float,
        val expiresAtMs: Long,
        val uninterruptible: Boolean = false,
        /**
         * 归层：true = 上身出招层（腿保持行走，可边跑边放），false = 全身运动层。
         * 服务端技能默认全身——压住移动是技能演出的常态；只控上身的技能显式指定
         */
        val toCombatLayer: Boolean = false,
        /**
         * 剪辑所在的动画库；null = 用目标当前外观/模型绑定的库。
         *
         * 技能动画绑死在外观库上是「换个物品技能就空放」的根源：外观随主手物品切换，
         * 库一换剪辑就没了。指令自带库之后，技能剪辑与外观彻底解耦——
         * 与本地动作（playerActions 的 file 字段）同一套思路
         */
        val library: net.minecraft.resources.ResourceLocation? = null,
        /** 服务端实例序号，finish 回带它让服务端按实例而不是按名匹配；-1 = 老协议无序号 */
        val seq: Long = -1L,
        /** 播完清层时的退出过渡秒数（服务端指令给定，不再借用上一个本地动作的收尾时长） */
        val exitSeconds: Float = 0.05f
    )

    private val forced = ConcurrentHashMap<UUID, ForcedAnimation>()
    private val paused = ConcurrentHashMap.newKeySet<UUID>()
    /** 暂停时刻（墙钟毫秒），恢复时把停掉的时长补回 once 到期戳 */
    private val pausedAtMs = ConcurrentHashMap<UUID, Long>()
    private val pendingSeek = ConcurrentHashMap<UUID, Float>()

    fun play(
        uuid: UUID,
        animation: String,
        transition: Int,
        loop: Boolean,
        speed: Float,
        expiresAtMs: Long,
        uninterruptible: Boolean = false,
        toCombatLayer: Boolean = false,
        library: net.minecraft.resources.ResourceLocation? = null,
        seq: Long = -1L,
        exitSeconds: Float = 0.05f
    ) {
        // 霸体拦截：正在播放的 uninterruptible 动画不被新 play 顶替（同名重播仍放行，
        // 供服务端刷新 once 到期戳）；显式 stop 不在此路径，始终可停
        val current = forced[uuid]
        if (current != null && current.uninterruptible && current.animation != animation) {
            return
        }
        forced[uuid] = ForcedAnimation(
            idCounter.incrementAndGet(), animation, transition, loop, speed,
            expiresAtMs, uninterruptible, toCombatLayer, library, seq, exitSeconds
        )
        paused.remove(uuid)
        pausedAtMs.remove(uuid)
        pendingSeek.remove(uuid)
    }

    /** 停止指定动画；animation 为 null 时清除该实体的全部记录。 */
    fun stop(uuid: UUID, animation: String?): Boolean {
        if (animation == null) {
            paused.remove(uuid)
            pausedAtMs.remove(uuid)
            pendingSeek.remove(uuid)
            return forced.remove(uuid) != null
        }
        val entry = forced[uuid] ?: return false
        if (entry.animation != animation || !forced.remove(uuid, entry)) return false
        paused.remove(uuid)
        pausedAtMs.remove(uuid)
        pendingSeek.remove(uuid)
        return true
    }

    fun get(uuid: UUID): ForcedAnimation? = forced[uuid]

    /** 暂停当前播控动画（冻结时间轴与 once 到期）；无播控记录时忽略 */
    fun pause(uuid: UUID) {
        if (forced.containsKey(uuid) && paused.add(uuid)) {
            pausedAtMs[uuid] = System.currentTimeMillis()
        }
    }

    /**
     * 恢复。once 的到期戳是墙钟，暂停期间照走——不补回停掉的时长，
     * 暂停超过剩余时长再恢复会在当帧直接到期，动画从暂停点凭空消失
     */
    fun resume(uuid: UUID) {
        if (!paused.remove(uuid)) return
        val pausedAt = pausedAtMs.remove(uuid) ?: return
        val entry = forced[uuid] ?: return
        if (entry.expiresAtMs <= 0L) return
        val shifted = entry.copy(expiresAtMs = entry.expiresAtMs + (System.currentTimeMillis() - pausedAt))
        forced.replace(uuid, entry, shifted)
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
        pausedAtMs.clear()
        pendingSeek.clear()
    }
}
