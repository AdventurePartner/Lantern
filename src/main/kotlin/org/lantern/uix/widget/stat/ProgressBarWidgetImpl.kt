package org.lantern.uix.widget.stat

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.hud.HudStat
import org.lantern.uix.widget.BaseWidget

/**
 * 进度条（经验条式）：`background` 整张画，`fill` 按 [stat] 的「值 ÷ 上限」裁切；
 * 两者都可带状态前缀（`poison-fill` 等）。
 */
class ProgressBarWidgetImpl(
    val stat: HudStat?,
    val textures: Map<String, String>
) : BaseWidget() {

    override val widgetType: String = "progress-bar"

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        // Rendering handled by ProgressBarRenderer via the canvas system
    }

    companion object {
        const val DEFAULT_WIDTH = 182
        const val DEFAULT_HEIGHT = 5
    }
}
