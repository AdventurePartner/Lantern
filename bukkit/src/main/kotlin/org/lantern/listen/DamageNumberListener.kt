package org.lantern.listen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.lantern.handler.CacheHandler
import org.lantern.worldimage.WorldImageService
import java.util.Locale
import java.util.UUID

/**
 * 伤害数值弹出（世界图片系统的首个应用）。
 *
 * MONITOR 只读不改伤害结算，数值取 finalDamage（修正后）。
 * 同一实体以固定 id "dmg-<uuid>" spawn：merge-ms 窗口外的下一跳是替换语义，
 * 客户端会把数字刷新为最新值并重启动画；窗口内的伤害不另起数字，当前数字播完为止。
 */
class DamageNumberListener : Listener {
    private val lastSpawn = HashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val cause = event.cause
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.CUSTOM) return
        val target = event.entity as? LivingEntity ?: return

        val cfg = CacheHandler.worldImages.damage
        if (!cfg.enabled) return
        val amount = event.finalDamage
        if (amount < cfg.minDamage) return

        val now = System.currentTimeMillis()
        val last = lastSpawn[target.uniqueId]
        if (last != null && now - last < cfg.mergeMs) return
        lastSpawn[target.uniqueId] = now
        if (lastSpawn.size > 256) {
            // 逐出已过刷新窗口的条目，防长期运行无界增长；正常流量到不了这个量
            lastSpawn.entries.removeIf { now - it.value > cfg.mergeMs }
        }

        val instance = JsonObject()
        instance.addProperty("id", "dmg-${target.uniqueId}")
        instance.addProperty("type", "text")
        instance.addProperty("text", String.format(Locale.ROOT, cfg.format, amount))
        instance.addProperty("color", cfg.color)
        instance.addProperty("size", cfg.size)
        instance.addProperty("scale", cfg.scale)
        instance.addProperty("animation", cfg.animation)
        instance.addProperty("first-person", cfg.firstPerson)
        // 多段伤害横向抖开，避免同一实体的连续数字叠在一条竖线上
        val bind = JsonObject()
        bind.addProperty("entity", target.uniqueId.toString())
        instance.add("bind", bind)
        val offset = JsonArray()
        offset.add((Math.random() - 0.5) * 0.3)
        offset.add(target.eyeHeight * 0.85)
        offset.add((Math.random() - 0.5) * 0.3)
        instance.add("offset", offset)

        WorldImageService.spawn(WorldImageService.receivers(target.location, cfg.radius), listOf(instance))
    }
}
