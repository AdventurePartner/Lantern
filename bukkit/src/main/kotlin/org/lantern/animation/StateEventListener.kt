package org.lantern.animation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.lantern.network.NetworkHandler

/**
 * 服务端事件驱动的状态动画（阶段三第二批）：heal（回血）/ attack（近战出手）。
 *
 * 复用 packet 15 播控通道下发（与 death 演出同构），客户端无需新逻辑：
 * states 里配置了 heal/attack 才生效，未配置的模型完全不受影响。
 * 濒死实体的播控入口卫兵（DeathAnimationInterceptor）自动拦截这两类指令。
 *
 * 限流：再生效果高频回血、同 tick 多目标命中（横扫）都会产生事件风暴，
 * per-entity 冷却避免播控被刷屏；冷却表在写入时惰性清理过期项防泄漏。
 */
class StateEventListener : Listener {

    companion object {
        private const val HEAL_COOLDOWN_MS = 1000L
        private const val ATTACK_COOLDOWN_MS = 300L

        private val healCooldowns = ConcurrentHashMap<UUID, Long>()
        private val attackCooldowns = ConcurrentHashMap<UUID, Long>()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRegainHealth(event: EntityRegainHealthEvent) {
        if (event.amount <= 0) return
        val entity = event.entity as? LivingEntity ?: return
        val entry = AnimationOrchestrator.stateEntryOf(entity, "heal") ?: return
        if (!tryAcquire(healCooldowns, entity.uniqueId, HEAL_COOLDOWN_MS)) return
        NetworkHandler.playAnimation(entity, entry.animation, entry.transition, loop = false)
    }

    /**
     * 动作无敌帧：窗口内玩家免疫伤害。
     *
     * 监听的是 EntityDamageEvent 而非 ByEntity——闪避要能躲开弹道、爆炸、火焰这些
     * 非直接攻击的来源。虚空与 /kill 例外，否则玩家会卡在世界外无法死亡
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInvulnerableDamage(event: org.bukkit.event.entity.EntityDamageEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val cause = event.cause
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID ||
            cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM
        ) return
        if (AnimationOrchestrator.isInvulnerable(player.uniqueId)) {
            event.isCancelled = true
        }
    }

    /**
     * 原版攻击压制：动作播放窗口内取消玩家的近战输出。
     * 优先级 HIGHEST 且不忽略已取消事件——要在伤害真正结算前拦下来，
     * 与下面 MONITOR 的动画触发分属两个阶段
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSuppressedAttack(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? org.bukkit.entity.Player ?: return
        if (AnimationOrchestrator.isAttackSuppressed(damager.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        // 只处理本体出手：抛射物命中走 pull_bow 客户端姿态，不在此发包
        val damager = event.damager as? LivingEntity ?: return
        val entry = AnimationOrchestrator.stateEntryOf(damager, "attack") ?: return
        if (!tryAcquire(attackCooldowns, damager.uniqueId, ATTACK_COOLDOWN_MS)) return
        NetworkHandler.playAnimation(damager, entry.animation, entry.transition, loop = false)
    }

    /** 冷却判定：未冷却返回 true 并记录本次时间；顺带清理全表过期项（表为在线实体数，量级小） */
    private fun tryAcquire(
        cooldowns: ConcurrentHashMap<UUID, Long>,
        uuid: UUID,
        cooldownMs: Long
    ): Boolean {
        val now = System.currentTimeMillis()
        val iterator = cooldowns.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= cooldownMs) iterator.remove()
        }
        val last = cooldowns.putIfAbsent(uuid, now)
        return last == null || now - last >= cooldownMs
    }
}
