package org.lantern.uix.widget.stat

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.hud.HudStat
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.BaseWidget

/**
 * 图标条（心、鸡腿式）：按 [stat] 的数值画一排满/半/空图标。
 * [textures] 的键为 `empty`/`half`/`full`，可带状态前缀（`poison-full` 等）、
 * `absorption-*`（吸收心）与 `blink-*`（受伤闪烁）。
 */
class IconBarWidgetImpl(
    val stat: HudStat?,
    val textures: Map<String, String>
) : BaseWidget() {

    override val widgetType: String = "icon-bar"

    val iconWidth: Int get() = style.getInt(StyleProperty.ICON_WIDTH, DEFAULT_ICON_SIZE)
    val iconHeight: Int get() = style.getInt(StyleProperty.ICON_HEIGHT, DEFAULT_ICON_SIZE)
    val iconSpacing: Int get() = style.getInt(StyleProperty.ICON_SPACING, DEFAULT_ICON_SPACING)
    val iconsPerRow: Int get() = style.getInt(StyleProperty.ICONS_PER_ROW, DEFAULT_ICONS_PER_ROW).coerceAtLeast(1)
    val rowSpacing: Int get() = style.getInt(StyleProperty.ROW_SPACING, DEFAULT_ROW_SPACING)

    /** 一行排满时的宽度（原生宽度）；从右往左排时以它的右缘为起点。 */
    val rowWidth: Int get() = (iconsPerRow - 1) * iconSpacing + iconWidth

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        // Rendering handled by IconBarRenderer via the canvas system
    }

    companion object {
        const val DEFAULT_ICON_SIZE = 9
        const val DEFAULT_ICON_SPACING = 8
        const val DEFAULT_ICONS_PER_ROW = 10
        const val DEFAULT_ROW_SPACING = 10
    }
}
