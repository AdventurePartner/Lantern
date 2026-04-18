package org.lantern.internal.storage

import org.lantern.uix.widget.IWidget

enum class ScreenType { HUD, GUI, OVERLAY }

data class UiScreenEntry(
    val rootWidget: IWidget,
    val type: ScreenType,
    val matchTitle: String? = null
)

/**
 * Client-side store for all UI screens received from the server.
 * Key is the screen id defined in the config YAML.
 */
object UiScreenStorage {

    private val screens = mutableMapOf<String, UiScreenEntry>()

    // Pre-built cache by ScreenType, rebuilt on mutation
    private val byType = mutableMapOf<ScreenType, Map<String, IWidget>>()

    fun put(id: String, rootWidget: IWidget, type: ScreenType, matchTitle: String? = null) {
        screens[id] = UiScreenEntry(rootWidget, type, matchTitle)
        rebuildCache()
    }

    fun get(id: String): UiScreenEntry? = screens[id]

    fun getAllOfType(type: ScreenType): Map<String, IWidget> =
        byType[type] ?: emptyMap()

    fun getOverlaysForTitle(title: String): List<IWidget> =
        screens.values
            .filter { it.type == ScreenType.OVERLAY && it.matchTitle == title }
            .map { it.rootWidget }

    fun clear() {
        screens.clear()
        byType.clear()
    }

    private fun rebuildCache() {
        byType.clear()
        for (type in ScreenType.entries) {
            val filtered = screens.filter { it.value.type == type }
            if (filtered.isNotEmpty()) {
                byType[type] = filtered.mapValues { it.value.rootWidget }
            }
        }
    }
}
