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
 * 同一实体以固定 id "dmg-<uuid>" spawn：merge-ms 是一个固定累计窗口——
 * 窗口内的多段伤害累加成一个数字并即时替换刷新（同 id 替换语义，动画重播、
 * 数字滚动增长），跨过窗口的下一跳重开累计。多段技能不再丢跳。
 */
class DamageNumberListener : Listener {

    /** 窗口锚点创建时定死（固定窗口，非滑动）：连续 DOT 不会无限续窗口 */
    private class Accumulator(val at: Long, var amount: Double)

    private val pending = HashMap<UUID, Accumulator>()

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
        var acc = pending[target.uniqueId]
        if (acc == null || now - acc.at >= cfg.mergeMs) {
            acc = Accumulator(now, 0.0)
            pending[target.uniqueId] = acc
        }
        acc.amount += amount
        val total = acc.amount
        if (pending.size > 256) {
            // 逐出已过窗口的条目，防长期运行无界增长；正常流量到不了这个量
            pending.entries.removeIf { now - it.value.at > cfg.mergeMs }
        }

        val instance = JsonObject()
        // 窗口起点进 id：新窗口是全新实例，上一窗口的累计数字自然播完淡出，
        // 而不是被新窗口的小数值瞬间顶掉
        instance.addProperty("id", "dmg-${target.uniqueId}-${acc.at}")
        instance.addProperty("type", "text")
        instance.addProperty("text", String.format(Locale.ROOT, cfg.format, total))
        instance.addProperty("color", cfg.color)
        instance.addProperty("size", cfg.size)
        instance.addProperty("scale", cfg.scale)
        instance.addProperty("animation", cfg.animation)
        instance.addProperty("first-person", cfg.firstPerson)
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
