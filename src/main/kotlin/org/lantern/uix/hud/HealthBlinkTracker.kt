package org.lantern.uix.hud

import net.minecraft.Util
import net.minecraft.client.player.LocalPlayer
import kotlin.math.ceil

/**
 * 复刻原版受伤闪烁：处于无敌帧时掉血闪 20 tick、回血闪 10 tick，期间 [displayHealth]
 * 停在变化前的血量，1 秒后追上当前血量。逻辑幂等，同一帧多个控件重复调用结果一致。
 */
object HealthBlinkTracker {

    private var trackedEntityId = Int.MIN_VALUE
    private var lastHealth = 0
    private var lastHealthTime = 0L
    private var blinkUntilTick = 0L

    var displayHealth = 0
        private set

    fun update(player: LocalPlayer) {
        val health = ceil(player.health).toInt()
        val now = Util.getMillis()
        val tick = player.tickCount.toLong()
        // 重生后是新实体、tickCount 归零，沿用旧的闪烁截止 tick 会一直闪
        if (player.id != trackedEntityId) {
            trackedEntityId = player.id
            lastHealth = health
            displayHealth = health
            lastHealthTime = now
            blinkUntilTick = 0L
        }
        if (health < lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = now
            blinkUntilTick = tick + 20
        } else if (health > lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = now
            blinkUntilTick = tick + 10
        }
        if (now - lastHealthTime > 1000L) {
            displayHealth = health
            lastHealthTime = now
        }
        lastHealth = health
    }

    fun blinking(player: LocalPlayer): Boolean {
        val tick = player.tickCount.toLong()
        return blinkUntilTick > tick && (blinkUntilTick - tick) / 3L % 2L == 1L
    }
}
