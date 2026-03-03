package org.lantern.internal.storage

import org.lantern.uix.widget.IWidget

enum class ScreenType { HUD, GUI }

data class UiScreenEntry(val rootWidget: IWidget, val type: ScreenType)

/**
 * Client-side store for all UI screens received from the server.
 * Key is the screen id defined in the config YAML.
 */
object UiScreenStorage {

    private val screens = mutableMapOf<String, UiScreenEntry>()

    fun put(id: String, rootWidget: IWidget, type: ScreenType) {
        screens[id] = UiScreenEntry(rootWidget, type)
    }

    fun get(id: String): UiScreenEntry? = screens[id]

    fun getAllOfType(type: ScreenType): Map<String, IWidget> =
        screens.filter { it.value.type == type }.mapValues { it.value.rootWidget }

    fun clear() {
        screens.clear()
    }
}
