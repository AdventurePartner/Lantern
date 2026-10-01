package org.lantern.internal.storage

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import org.lantern.uix.hud.VanillaHudElement
import org.lantern.uix.hud.VanillaHudVisibility
import org.lantern.uix.widget.IWidget
import java.util.EnumSet

enum class ScreenType { HUD, GUI, OVERLAY }

/**
 * HUD 的两个渲染层（与 overlay 的 index 语义一致，数值越大越靠前）：
 * - [BASE]：index < 0，画进原版 HUD 图层栈（相机覆盖层之上、准星/快捷栏/聊天之下），
 *   可见性跟随原版 HUD（界面打开时仍绘制），不接收点击/悬停——替代原版元素的配置放这里；
 * - [TOP]：index >= 0（默认），画在全部原版 HUD 之上，任何界面打开时隐藏。
 */
enum class HudLayer { BASE, TOP }

/**
 * z 轴语义（数值越大渲染越靠前/越晚画/越在上）：
 * - index < 0：背景层，画在容器物品/槽位之下（仅容器类界面有此锚点）；HUD 则为原版层；
 * - index >= 0：顶层，画在物品/tooltip 之上（默认 0，兼容旧配置）。
 */
data class UiScreenEntry(
    val rootWidget: IWidget,
    val type: ScreenType,
    val matchTitle: String? = null,
    val matchScreen: String? = null,
    val index: Int = 0,
    val cancelVanillaBg: Boolean = false,
    val hideVanilla: Set<VanillaHudElement> = emptySet()
)

/**
 * 一条命中的 overlay 及其定位模式。
 * [containerAnchored] 为 true 时布局原点平移到容器面板左上角
 * （AbstractContainerScreen 的 leftPos/topPos），y 取负值即可放到面板上方。
 */
data class OverlayMatch(
    val rootWidget: IWidget,
    val containerAnchored: Boolean,
    val index: Int = 0,
    val cancelVanillaBg: Boolean = false
)

/**
 * Client-side store for all UI screens received from the server.
 * Key is the screen id defined in the config YAML.
 */
object UiScreenStorage {

    private val screens = mutableMapOf<String, UiScreenEntry>()

    // Pre-built cache by ScreenType, rebuilt on mutation
    private val byType = mutableMapOf<ScreenType, Map<String, IWidget>>()

    private var hudBase: List<IWidget> = emptyList()
    private var hudTop: List<IWidget> = emptyList()

    fun put(
        id: String,
        rootWidget: IWidget,
        type: ScreenType,
        matchTitle: String? = null,
        matchScreen: String? = null,
        index: Int = 0,
        cancelVanillaBg: Boolean = false,
        hideVanilla: Set<VanillaHudElement> = emptySet()
    ) {
        screens[id] = UiScreenEntry(rootWidget, type, matchTitle, matchScreen, index, cancelVanillaBg, hideVanilla)
        rebuildCache()
    }

    fun get(id: String): UiScreenEntry? = screens[id]

    fun getAllOfType(type: ScreenType): Map<String, IWidget> =
        byType[type] ?: emptyMap()

    /** 指定层的 HUD 根节点，按 index 升序（稳定排序，同 index 保持下发顺序）。 */
    fun hudRoots(layer: HudLayer): List<IWidget> =
        if (layer == HudLayer.BASE) hudBase else hudTop

    /**
     * 返回所有命中当前 Screen 的 overlay，按 index 升序排列
     * （数值越大越靠前=越晚画；稳定排序，同 index 保持下发顺序）。
     * 优先按 [match-screen] 界面类型匹配（不受客户端语言影响），
     * 否则回退到 [match-title] 标题精确匹配（沿用旧语义，原点为屏幕左上角）。
     */
    fun getOverlaysFor(screen: Screen): List<OverlayMatch> =
        screens.values
            .filter { it.type == ScreenType.OVERLAY }
            .mapNotNull { entry ->
                if (matches(entry, screen)) {
                    OverlayMatch(
                        entry.rootWidget,
                        containerAnchored = entry.matchScreen != null,
                        index = entry.index,
                        cancelVanillaBg = entry.cancelVanillaBg
                    )
                } else {
                    null
                }
            }
            .sortedBy { it.index }

    private fun matches(entry: UiScreenEntry, screen: Screen): Boolean {
        if (entry.matchScreen != null) {
            return entry.matchScreen == screenKey(screen)
        }
        if (entry.matchTitle != null) {
            return entry.matchTitle == screen.title.string
        }
        return false
    }

    /** match-screen 取值与 Screen 类型的映射；新增可接管界面时在此登记。 */
    fun screenKey(screen: Screen): String? = when (screen) {
        is InventoryScreen -> "player_inventory"
        else -> null
    }

    fun clear() {
        screens.clear()
        byType.clear()
        hudBase = emptyList()
        hudTop = emptyList()
        VanillaHudVisibility.update(emptySet())
    }

    private fun rebuildCache() {
        byType.clear()
        for (type in ScreenType.entries) {
            val filtered = screens.filter { it.value.type == type }
            if (filtered.isNotEmpty()) {
                byType[type] = filtered.mapValues { it.value.rootWidget }
            }
        }
        val huds = screens.values.filter { it.type == ScreenType.HUD }.sortedBy { it.index }
        hudBase = huds.filter { it.index < 0 }.map { it.rootWidget }
        hudTop = huds.filter { it.index >= 0 }.map { it.rootWidget }
        VanillaHudVisibility.update(
            huds.flatMapTo(EnumSet.noneOf(VanillaHudElement::class.java)) { it.hideVanilla }
        )
    }
}
