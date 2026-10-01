package org.lantern.uix.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * 自绘控件（icon-bar / progress-bar）的数据来源，对应节点的 `source`。
 * 只用 1.20.1 / 1.21.1 / 1.21.10 源码写法一致的 API（MobEffect 与 Holder 的差异由各模块分别编译吸收）。
 */
enum class HudStat(
    val key: String,
    /** 原版图标的排列方向：true 为从右往左（饥饿、氧气、坐骑血量）。 */
    val rightToLeft: Boolean
) {
    HEALTH("health", false),
    ABSORPTION("absorption", false),
    FOOD("food", true),
    SATURATION("saturation", true),
    ARMOR("armor", false),
    ARMOR_TOUGHNESS("armor_toughness", false),
    AIR("air", true),
    VEHICLE_HEALTH("vehicle_health", true),
    EXPERIENCE("experience", false),
    JUMP("jump", false);

    fun value(player: LocalPlayer): Float = when (this) {
        HEALTH -> player.health
        ABSORPTION -> player.absorptionAmount
        FOOD -> player.foodData.foodLevel.toFloat()
        SATURATION -> player.foodData.saturationLevel
        ARMOR -> player.armorValue.toFloat()
        ARMOR_TOUGHNESS -> player.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
        AIR -> player.airSupply.coerceIn(0, player.maxAirSupply).toFloat()
        VEHICLE_HEALTH -> vehicleOf(player)?.health ?: 0f
        EXPERIENCE -> player.experienceProgress
        JUMP -> player.jumpRidingScale
    }

    fun max(player: LocalPlayer): Float = when (this) {
        HEALTH -> maxOf(player.maxHealth, player.health)
        ABSORPTION -> player.maxHealth
        FOOD, SATURATION, ARMOR, ARMOR_TOUGHNESS -> 20f
        AIR -> player.maxAirSupply.toFloat()
        VEHICLE_HEALTH -> vehicleOf(player)?.maxHealth ?: 0f
        EXPERIENCE, JUMP -> 1f
    }

    /** icon-bar 默认每个图标代表的数值（原版：一颗心 = 2 点血，10 个气泡分完氧气）。 */
    fun defaultValuePerIcon(max: Float): Float = when (this) {
        AIR -> max / 10f
        EXPERIENCE, JUMP -> 0.1f
        else -> 2f
    }

    /** `auto-hide` 时是否显示，沿用原版对应元素的显示条件。 */
    fun visible(player: LocalPlayer): Boolean {
        val gameMode = Minecraft.getInstance().gameMode
        val survival = gameMode?.canHurtPlayer() ?: true
        return when (this) {
            HEALTH -> survival
            ABSORPTION -> survival && player.absorptionAmount > 0f
            FOOD, SATURATION -> survival && vehicleOf(player) == null
            ARMOR -> survival && player.armorValue > 0
            ARMOR_TOUGHNESS -> survival && value(player) > 0f
            AIR -> survival && (player.isEyeInFluid(FluidTags.WATER) || player.airSupply < player.maxAirSupply)
            VEHICLE_HEALTH -> vehicleOf(player) != null
            EXPERIENCE -> gameMode?.hasExperience() ?: true
            JUMP -> player.jumpableVehicle() != null
        }
    }

    /** 贴图状态前缀（textures 里的 `<状态>-<部位>`），null 为常规状态；优先级同原版心形类型。 */
    fun state(player: LocalPlayer): String? = when (this) {
        HEALTH -> when {
            player.hasEffect(MobEffects.POISON) -> "poison"
            player.hasEffect(MobEffects.WITHER) -> "wither"
            player.isFullyFrozen -> "frozen"
            else -> null
        }
        FOOD, SATURATION -> if (player.hasEffect(MobEffects.HUNGER)) "hunger" else null
        else -> null
    }

    companion object {
        private val BY_KEY = entries.associateBy { it.key }

        fun fromKey(key: String?): HudStat? = key?.trim()?.lowercase()?.let { BY_KEY[it] }

        /** 原版只给显示血量的活体坐骑画坐骑血量，此时饥饿条让位。 */
        fun vehicleOf(player: LocalPlayer): LivingEntity? =
            (player.vehicle as? LivingEntity)?.takeIf { it.showVehicleHealth() }
    }
}
