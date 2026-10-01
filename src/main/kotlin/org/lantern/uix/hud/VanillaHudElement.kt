package org.lantern.uix.hud

import java.util.EnumSet

/**
 * 原版 HUD 元素：可被 HUD 配置 `hide-vanilla` 隐藏，也可被 `type: vanilla` 节点摆放。
 * 新增元素：在此加一项（原生尺寸、对齐方式、原版默认布局参考点），再在三处平台挂钩
 * （1.21.1 GuiVanillaHudMixin、Forge 1.20.1 ForgeClientListener、NeoForge NeoForgeClientEvents）
 * 同时补上隐藏与摆放的映射。聊天、Tab 列表、标题、调试屏刻意不纳入。
 */
enum class VanillaHudElement(
    val key: String,
    val naturalWidth: Int,
    val naturalHeight: Int,
    val align: VanillaHudAlign
) {
    HOTBAR("hotbar", 182, 22, VanillaHudAlign.TOP_LEFT),
    SELECTED_ITEM_NAME("selected_item_name", 182, 9, VanillaHudAlign.TOP_CENTER),
    HEALTH("health", 81, 9, VanillaHudAlign.TOP_LEFT),
    ARMOR("armor", 81, 9, VanillaHudAlign.TOP_LEFT),
    FOOD("food", 81, 9, VanillaHudAlign.TOP_RIGHT),
    AIR("air", 81, 9, VanillaHudAlign.TOP_RIGHT),
    VEHICLE_HEALTH("vehicle_health", 81, 9, VanillaHudAlign.TOP_RIGHT),
    EXPERIENCE_BAR("experience_bar", 182, 5, VanillaHudAlign.TOP_LEFT),
    EXPERIENCE_LEVEL("experience_level", 182, 9, VanillaHudAlign.TOP_CENTER),
    JUMP_BAR("jump_bar", 182, 5, VanillaHudAlign.TOP_LEFT),
    CROSSHAIR("crosshair", 15, 15, VanillaHudAlign.TOP_LEFT),
    EFFECTS("effects", 25, 51, VanillaHudAlign.TOP_RIGHT);

    /**
     * 元素在原版默认布局（一行心、元素齐全、可受伤模式）里的参考点 x，含义随 [align]：
     * 左上角 / 顶边中点 / 右上角。`stacking: vanilla` 的节点以它为基准整体平移。
     */
    fun nominalX(guiWidth: Int): Int = when (this) {
        HOTBAR, HEALTH, ARMOR, EXPERIENCE_BAR, JUMP_BAR -> guiWidth / 2 - 91
        FOOD, AIR, VEHICLE_HEALTH -> guiWidth / 2 + 91
        SELECTED_ITEM_NAME, EXPERIENCE_LEVEL -> guiWidth / 2
        CROSSHAIR -> (guiWidth - 15) / 2
        EFFECTS -> guiWidth
    }

    fun nominalY(guiHeight: Int): Int = when (this) {
        HOTBAR -> guiHeight - 22
        SELECTED_ITEM_NAME -> guiHeight - 59
        HEALTH, FOOD, VEHICLE_HEALTH -> guiHeight - 39
        ARMOR, AIR -> guiHeight - 49
        EXPERIENCE_BAR, JUMP_BAR -> guiHeight - 29
        EXPERIENCE_LEVEL -> guiHeight - 35
        CROSSHAIR -> (guiHeight - 15) / 2
        EFFECTS -> 0
    }

    companion object {
        const val ALL = "all"
        private val BY_KEY = entries.associateBy { it.key }

        fun fromKey(key: String?): VanillaHudElement? = key?.trim()?.lowercase()?.let { BY_KEY[it] }

        /** 解析单个 id；`all` 展开为全部元素，未知 id 返回空集。 */
        fun parse(key: String): Set<VanillaHudElement> {
            val normalized = key.trim().lowercase()
            if (normalized == ALL) return EnumSet.allOf(VanillaHudElement::class.java)
            return BY_KEY[normalized]?.let { EnumSet.of(it) } ?: emptySet()
        }
    }
}

/** `vanilla` 节点框与原版元素的对齐点：左上角 / 顶边中点 / 右上角。 */
enum class VanillaHudAlign { TOP_LEFT, TOP_CENTER, TOP_RIGHT }

/**
 * 当前被隐藏的原版 HUD 元素：所有已加载 HUD 配置的 `hide-vanilla` 并集。
 * 由 UiScreenStorage 在配置变更/清空时刷新，各平台渲染挂钩每帧只读查询。
 */
object VanillaHudVisibility {

    @Volatile
    private var hidden: Set<VanillaHudElement> = emptySet()

    fun update(elements: Set<VanillaHudElement>) {
        hidden = if (elements.isEmpty()) emptySet() else EnumSet.copyOf(elements)
    }

    @JvmStatic
    fun isHidden(element: VanillaHudElement): Boolean = element in hidden
}
