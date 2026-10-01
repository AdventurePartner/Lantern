package org.lantern.uix.renderer.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.effect.MobEffects
import org.lantern.uix.hud.HealthBlinkTracker
import org.lantern.uix.hud.HudStat
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.renderer.BackgroundPainter
import org.lantern.uix.renderer.IWidgetRenderer
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.style.StyleRule
import org.lantern.uix.widget.stat.IconBarWidgetImpl
import java.util.Random
import kotlin.math.ceil

/**
 * 图标条：每个图标位先画空槽（empty），再按剩余的「半格」数画满或半。
 * 吸收心接在常规图标之后；受伤闪烁时先画 blink 层（变化前的血量）再画当前血量，同原版。
 */
object IconBarRenderer : IWidgetRenderer<IconBarWidgetImpl> {

    private const val MAX_ICONS = 200
    private const val ABSORPTION = "absorption"
    private const val BLINK = "blink"

    // 与原版一致：每 tick 换一次种子，同一 tick 内抖动稳定
    private val random = Random()

    override fun render(
        widget: IconBarWidgetImpl,
        style: StyleRule,
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        if (!style.getBoolean(StyleProperty.VISIBLE)) return
        val stat = widget.stat ?: return
        val player = Minecraft.getInstance().player ?: return
        if (style.getBoolean(StyleProperty.AUTO_HIDE) && !stat.visible(player)) return

        val rect = LayoutCache.findRect(widget)
        val x = rect?.x ?: style.getInt(StyleProperty.X)
        val y = rect?.y ?: style.getInt(StyleProperty.Y)
        BackgroundPainter.paint(
            graphics, style, x, y,
            WidgetGeometry.width(widget, widget.rowWidth),
            WidgetGeometry.height(widget, widget.iconHeight)
        )

        val max = stat.max(player)
        val perIcon = style.getFloat(StyleProperty.VALUE_PER_ICON).takeIf { it > 0f }
            ?: stat.defaultValuePerIcon(max)
        if (max <= 0f || perIcon <= 0f) return

        val textures = widget.textures
        val state = stat.state(player)
        val baseIcons = ceil(max / perIcon).toInt()
        val halves = halfUnits(stat.value(player), perIcon)
        val absorption = if (stat == HudStat.HEALTH && hasPrefix(textures, ABSORPTION)) player.absorptionAmount else 0f
        val absorptionHalves = halfUnits(absorption, perIcon)
        val total = (baseIcons + (absorptionHalves + 1) / 2).coerceAtMost(MAX_ICONS)

        val blinking = if (stat == HudStat.HEALTH && hasPrefix(textures, BLINK)) {
            HealthBlinkTracker.update(player)
            HealthBlinkTracker.blinking(player)
        } else {
            false
        }
        val displayHalves = if (blinking) halfUnits(HealthBlinkTracker.displayHealth.toFloat(), perIcon) else 0

        val animate = style.getBoolean(StyleProperty.ANIMATE, false)
        val tick = player.tickCount
        random.setSeed(tick * 312871L)
        val lowHealthShake = animate && stat == HudStat.HEALTH && player.health + player.absorptionAmount <= 4f
        val regenIndex = if (animate && stat == HudStat.HEALTH && player.hasEffect(MobEffects.REGENERATION)) {
            tick % ceil(max + 5f).toInt()
        } else {
            -1
        }
        val food = player.foodData
        val hungerShake = animate && (stat == HudStat.FOOD || stat == HudStat.SATURATION) &&
            food.saturationLevel <= 0f && tick % (food.foodLevel * 3 + 1) == 0

        val rightToLeft = when (style.getString(StyleProperty.FILL_DIRECTION, "").lowercase()) {
            "left" -> true
            "right" -> false
            else -> stat.rightToLeft
        }
        val rowsUp = !style.getString(StyleProperty.ROW_DIRECTION, "up").equals("down", ignoreCase = true)
        val iconWidth = widget.iconWidth
        val iconHeight = widget.iconHeight
        val spacing = widget.iconSpacing
        val perRow = widget.iconsPerRow
        val rowSpacing = widget.rowSpacing
        val rowWidth = widget.rowWidth
        val empty = (if (blinking) textures["$BLINK-empty"] else null) ?: stateTexture(textures, state, "empty")

        for (i in 0 until total) {
            val column = i % perRow
            val row = i / perRow
            val iconX = x + if (rightToLeft) rowWidth - iconWidth - column * spacing else column * spacing
            var iconY = y + if (rowsUp) -row * rowSpacing else row * rowSpacing
            if (lowHealthShake) iconY += random.nextInt(2)
            if (hungerShake) iconY += random.nextInt(3) - 1
            if (i == regenIndex) iconY -= 2

            empty?.let { ImageRenderer.drawTexture(graphics, it, iconX, iconY, iconWidth, iconHeight) }
            if (i < baseIcons) {
                if (blinking) {
                    fillTexture(textures, BLINK, true, displayHalves - i * 2)
                        ?.let { ImageRenderer.drawTexture(graphics, it, iconX, iconY, iconWidth, iconHeight) }
                }
                fillTexture(textures, state, false, halves - i * 2)
                    ?.let { ImageRenderer.drawTexture(graphics, it, iconX, iconY, iconWidth, iconHeight) }
            } else {
                fillTexture(textures, ABSORPTION, true, absorptionHalves - (i - baseIcons) * 2)
                    ?.let { ImageRenderer.drawTexture(graphics, it, iconX, iconY, iconWidth, iconHeight) }
            }
        }
    }

    /** 数值折算成「半格」数，向上取整（同原版对血量取 ceil）。 */
    private fun halfUnits(value: Float, perIcon: Float): Int =
        if (value <= 0f) 0 else ceil(value / perIcon * 2f - 1e-4f).toInt()

    private fun hasPrefix(textures: Map<String, String>, prefix: String): Boolean =
        textures.keys.any { it.startsWith("$prefix-") }

    /** 先找 `<状态>-<部位>`，没有就用 `<部位>`。 */
    private fun stateTexture(textures: Map<String, String>, state: String?, part: String): String? =
        state?.let { textures["$it-$part"] } ?: textures[part]

    /**
     * 剩余 ≥ 2 个半格画满、1 个画半（缺 half 时用 full）。
     * [strict] 前缀（吸收、闪烁）只认带前缀的贴图，不回退到常规贴图。
     */
    private fun fillTexture(
        textures: Map<String, String>,
        prefix: String?,
        strict: Boolean,
        remainingHalves: Int
    ): String? {
        if (remainingHalves <= 0) return null
        val part = if (remainingHalves >= 2) "full" else "half"
        val lookup = { name: String ->
            if (strict) textures["$prefix-$name"] else stateTexture(textures, prefix, name)
        }
        return lookup(part) ?: lookup("full")
    }
}
