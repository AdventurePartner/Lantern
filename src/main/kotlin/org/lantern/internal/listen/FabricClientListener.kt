package org.lantern.internal.listen

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.network.PacketNetwork
import org.lwjgl.glfw.GLFW

object FabricClientListener {

    private val pressedKeys = mutableSetOf<String>()
    private val parsedKeys = mutableMapOf<String, ParsedKey>()

    fun register() {
        // Poll keyboard each tick for runtime-configured key bindings.
        ClientTickEvents.END_CLIENT_TICK.register tick@{ client ->
            if (client.screen != null) return@tick

            handleKeyboardInput(client)
        }
    }

    private fun handleKeyboardInput(client: Minecraft) {
        val window = client.window.window
        val keyboards = ResourceHandler.getKeyboards()

        keyboards.forEach { (spec, wrapper) ->
            val key = parsedKeys.computeIfAbsent(spec) { parseKeySpec(it) }
            val isDown = isDown(window, key)
            val wasDown = pressedKeys.contains(spec)

            if (isDown == wasDown) {
                return@forEach
            }

            if (isDown) {
                pressedKeys.add(spec)
                if (wrapper.press) {
                    PacketNetwork.sendKeyboardPacket(spec, true)
                }
            } else {
                pressedKeys.remove(spec)
                if (!wrapper.press) {
                    PacketNetwork.sendKeyboardPacket(spec, false)
                }
            }
        }
    }

    private fun isDown(window: Long, key: ParsedKey): Boolean {
        if (key.keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_CONTROL != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_SHIFT != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_ALT != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_SUPER != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)) return false
        }
        return GLFW.glfwGetKey(window, key.keyCode) == GLFW.GLFW_PRESS
    }

    private fun isAnyDown(window: Long, left: Int, right: Int): Boolean {
        return GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS
    }

    private fun parseKeySpec(spec: String): ParsedKey {
        val parts = spec.lowercase().split("+").map { it.trim() }.filter { it.isNotEmpty() }
        var mods = 0
        var main = ""

        for (part in parts) {
            when (part) {
                "ctrl", "control" -> mods = mods or GLFW.GLFW_MOD_CONTROL
                "shift" -> mods = mods or GLFW.GLFW_MOD_SHIFT
                "alt" -> mods = mods or GLFW.GLFW_MOD_ALT
                "super", "win", "meta", "cmd" -> mods = mods or GLFW.GLFW_MOD_SUPER
                else -> main = part
            }
        }

        val keyCode = when {
            main.length == 1 && main[0] in 'a'..'z' -> GLFW.GLFW_KEY_A + (main[0] - 'a')
            main.length == 1 && main[0] in '0'..'9' -> GLFW.GLFW_KEY_0 + (main[0] - '0')
            main.startsWith("f") && main.drop(1).toIntOrNull() in 1..25 -> {
                GLFW.GLFW_KEY_F1 + (main.drop(1).toInt() - 1)
            }
            main == "space" -> GLFW.GLFW_KEY_SPACE
            main == "enter" -> GLFW.GLFW_KEY_ENTER
            main == "tab" -> GLFW.GLFW_KEY_TAB
            main == "escape" || main == "esc" -> GLFW.GLFW_KEY_ESCAPE
            main == "backspace" -> GLFW.GLFW_KEY_BACKSPACE
            main == "delete" -> GLFW.GLFW_KEY_DELETE
            main == "insert" -> GLFW.GLFW_KEY_INSERT
            main == "home" -> GLFW.GLFW_KEY_HOME
            main == "end" -> GLFW.GLFW_KEY_END
            main == "pageup" -> GLFW.GLFW_KEY_PAGE_UP
            main == "pagedown" -> GLFW.GLFW_KEY_PAGE_DOWN
            main == "up" -> GLFW.GLFW_KEY_UP
            main == "down" -> GLFW.GLFW_KEY_DOWN
            main == "left" -> GLFW.GLFW_KEY_LEFT
            main == "right" -> GLFW.GLFW_KEY_RIGHT
            main == "ctrl" || main == "control" -> GLFW.GLFW_KEY_LEFT_CONTROL
            main == "shift" -> GLFW.GLFW_KEY_LEFT_SHIFT
            main == "alt" -> GLFW.GLFW_KEY_LEFT_ALT
            main == "super" || main == "win" || main == "meta" || main == "cmd" -> GLFW.GLFW_KEY_LEFT_SUPER
            else -> GLFW.GLFW_KEY_UNKNOWN
        }

        return ParsedKey(keyCode, mods)
    }
}

private data class ParsedKey(val keyCode: Int, val requiredMods: Int)

