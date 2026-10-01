package org.lantern.uix.hud

import java.util.EnumSet

/**
 * 可被 HUD 配置 `hide-vanilla` 隐藏的原版 HUD 元素。
 * 新增元素：在此加一项，再在三处平台挂钩（1.21.1 GuiVanillaHudMixin、Forge 1.20.1 overlay 映射、
 * NeoForge layer 映射）补上对应关系。聊天、Tab 列表、标题、调试屏刻意不纳入。
 */
enum class VanillaHudElement(val key: String) {
    HOTBAR("hotbar"),
    SELECTED_ITEM_NAME("selected_item_name"),
    HEALTH("health"),
    ARMOR("armor"),
    FOOD("food"),
    AIR("air"),
    VEHICLE_HEALTH("vehicle_health"),
    EXPERIENCE_BAR("experience_bar"),
    EXPERIENCE_LEVEL("experience_level"),
    JUMP_BAR("jump_bar"),
    CROSSHAIR("crosshair"),
    EFFECTS("effects");

    companion object {
        const val ALL = "all"
        private val BY_KEY = entries.associateBy { it.key }

        /** 解析单个 id；`all` 展开为全部元素，未知 id 返回空集。 */
        fun parse(key: String): Set<VanillaHudElement> {
            val normalized = key.trim().lowercase()
            if (normalized == ALL) return EnumSet.allOf(VanillaHudElement::class.java)
            return BY_KEY[normalized]?.let { EnumSet.of(it) } ?: emptySet()
        }
    }
}

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
