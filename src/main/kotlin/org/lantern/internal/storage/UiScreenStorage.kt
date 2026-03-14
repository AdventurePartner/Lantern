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

    fun put(id: String, rootWidget: IWidget, type: ScreenType, matchTitle: String? = null) {
        screens[id] = UiScreenEntry(rootWidget, type, matchTitle)
    }

    fun get(id: String): UiScreenEntry? = screens[id]

    fun getAllOfType(type: ScreenType): Map<String, IWidget> =
        screens.filter { it.value.type == type }.mapValues { it.value.rootWidget }

    fun getOverlaysForTitle(title: String): List<IWidget> =
        screens.values
            .filter { it.type == ScreenType.OVERLAY && it.matchTitle == title }
            .map { it.rootWidget }

    fun clear() {
        screens.clear()
    }
}
