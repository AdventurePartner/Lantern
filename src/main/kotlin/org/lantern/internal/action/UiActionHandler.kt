package org.lantern.internal.action

import net.minecraft.client.Minecraft

/**
 * Executes action strings attached to buttons.
 *
 * Supported formats:
 *   close              — close the current screen
 *   command:<cmd>      — run a chat command (slash optional)
 *   open:<screen-id>   — open another GUI screen by id
 */
object UiActionHandler {

    fun execute(action: String) {
        if (action.isBlank()) return
        val mc = Minecraft.getInstance()
        when {
            action == "close" -> mc.execute { mc.setScreen(null) }

            action.startsWith("command:") -> {
                val cmd = action.removePrefix("command:").trimStart('/')
                mc.execute {
                    mc.setScreen(null)
                    mc.player?.connection?.sendCommand(cmd)
                }
            }

            action.startsWith("open:") -> {
                val screenId = action.removePrefix("open:")
                mc.execute { openGui(screenId) }
            }
        }
    }

    private fun openGui(screenId: String) {
        val mc = Minecraft.getInstance()
        val entry = org.lantern.internal.storage.UiScreenStorage.get(screenId) ?: return
        if (entry.type != org.lantern.internal.storage.ScreenType.GUI) return
        mc.setScreen(org.lantern.uix.canvas.impl.GuiCanvas(screenId, entry.rootWidget))
    }
}
