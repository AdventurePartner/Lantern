package org.lantern.uix.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

/**
 * text 控件里的客户端实时数值（`{health}` 等），每帧绘制前替换；
 * 不认识的 `{...}` 原样保留。与服务端按 update-interval 刷新的 `%papi%` 变量互不干扰。
 */
object HudStatTokens {

    private val TOKEN = Regex("\\{([a-z_]+)\\}")

    fun resolve(text: String): String {
        if (text.indexOf('{') < 0) return text
        val player = Minecraft.getInstance().player ?: return text
        return TOKEN.replace(text) { match -> valueOf(match.groupValues[1], player) ?: match.value }
    }

    private fun valueOf(token: String, player: LocalPlayer): String? = when (token) {
        "health" -> decimal(player.health)
        "max_health" -> decimal(player.maxHealth)
        "absorption" -> decimal(player.absorptionAmount)
        "food" -> player.foodData.foodLevel.toString()
        "saturation" -> decimal(player.foodData.saturationLevel)
        "armor" -> player.armorValue.toString()
        "armor_toughness" -> decimal(HudStat.ARMOR_TOUGHNESS.value(player))
        "air" -> player.airSupply.coerceAtLeast(0).toString()
        "max_air" -> player.maxAirSupply.toString()
        "level" -> player.experienceLevel.toString()
        "exp_progress" -> (player.experienceProgress * 100f).toInt().toString()
        "vehicle_health" -> decimal(HudStat.vehicleOf(player)?.health ?: 0f)
        "vehicle_max_health" -> decimal(HudStat.vehicleOf(player)?.maxHealth ?: 0f)
        else -> null
    }

    /** 保留一位小数，整数不带 `.0`。 */
    private fun decimal(value: Float): String {
        val tenths = Math.round(value * 10f)
        return if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10f).toString()
    }
}
