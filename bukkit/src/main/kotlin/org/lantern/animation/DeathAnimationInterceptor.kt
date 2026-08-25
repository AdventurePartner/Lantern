package org.lantern.animation

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler

/**
 * 死亡动画拦截：原版尸体 20 tick 即移除，长 die 动画（如 3 秒）播不完。
 *
 * 流程：致命伤害 -> 取消并进入濒死态（无敌+停 AI+播控播放 death 动画 once）
 * -> 客户端播完回报 finish -> 真正击杀（health=0，掉落与 MM ~onDeath 正常触发）
 * -> 客户端 death 状态与播控为同一剪辑实例，不重播，尸体保持末帧至实体移除。
 *
 * 客户端离线等异常由 [FALLBACK_KILL_TICKS] 强制击杀兜底，生物不会永生。
 */
class DeathAnimationInterceptor : Listener {

    private val dying = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<UUID, Boolean>())

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        val entity = event.entity as? LivingEntity ?: return
        if (dying.contains(entity.uniqueId)) {
            event.isCancelled = true
            return
        }
        if (event.finalDamage < entity.health) return
        val deathAnim = AnimationOrchestrator.deathAnimationOf(entity) ?: return
        event.isCancelled = true
        dying.add(entity.uniqueId)
        (entity as? org.bukkit.entity.Mob)?.setAI(false)
        entity.isInvulnerable = true
        NetworkHandler.playAnimation(entity, deathAnim, 0, loop = false)
        Bukkit.getScheduler().runTaskLater(LanternPlugin.instance, Runnable { forceKill(entity) }, FALLBACK_KILL_TICKS)
    }

    @EventHandler
    fun onAnimationFinish(event: LanternAnimationFinishEvent) {
        val entity = event.animEntity as? LivingEntity ?: return
        if (!dying.remove(entity.uniqueId)) return
        if (event.animationName != AnimationOrchestrator.deathAnimationOf(entity)) return
        // 已从濒死集合移除，health=0 不再被拦截；不触发伤害事件避免递归
        if (entity.isValid) {
            entity.isInvulnerable = false
            (entity as? org.bukkit.entity.Mob)?.setAI(true)
            entity.health = 0.0
        }
    }

    private fun forceKill(entity: LivingEntity) {
        if (!dying.remove(entity.uniqueId)) return
        if (entity.isValid) {
            entity.isInvulnerable = false
            entity.health = 0.0
        }
    }

    private companion object {
        const val FALLBACK_KILL_TICKS = 300L
    }
}
