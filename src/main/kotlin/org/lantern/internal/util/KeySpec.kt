package org.lantern.internal.util

import org.lwjgl.glfw.GLFW

/**
 * 服务端下发的按键写法（"r"、"ctrl+q"、"left_alt"）解析成 GLFW 键码 + 修饰键位，
 * 与各平台监听器里 keys.yml 轮询的解析规则一致
 */
class KeySpec private constructor(val keyCode: Int, val requiredMods: Int) {

    fun isDown(window: Long): Boolean {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return false
        if (requiredMods and GLFW.GLFW_MOD_CONTROL != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)
        ) return false
        if (requiredMods and GLFW.GLFW_MOD_SHIFT != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)
        ) return false
        if (requiredMods and GLFW.GLFW_MOD_ALT != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)
        ) return false
        if (requiredMods and GLFW.GLFW_MOD_SUPER != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)
        ) return false
        return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS
    }

    private fun isAnyDown(window: Long, left: Int, right: Int): Boolean =
        GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS

    companion object {
        fun parse(spec: String): KeySpec {
            val parts = spec.lowercase().split('+').map(String::trim).filter(String::isNotEmpty)
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
            // 只写了修饰键、没写主键时（"alt"），把它本身当主键，否则永远不会触发
            if (main.isEmpty() && parts.size == 1) {
                main = parts[0]
                mods = 0
            }
            return KeySpec(keyCodeOf(main), mods)
        }

        private fun keyCodeOf(main: String): Int = when {
            main.length == 1 && main[0] in 'a'..'z' -> GLFW.GLFW_KEY_A + (main[0] - 'a')
            main.length == 1 && main[0] in '0'..'9' -> GLFW.GLFW_KEY_0 + (main[0] - '0')
            main.startsWith("f") && main.drop(1).toIntOrNull() in 1..25 ->
                GLFW.GLFW_KEY_F1 + (main.drop(1).toInt() - 1)
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
            main == "ctrl" || main == "control" || main == "left_ctrl" || main == "lctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL
            main == "right_ctrl" || main == "rctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL
            main == "shift" || main == "left_shift" || main == "lshift" -> GLFW.GLFW_KEY_LEFT_SHIFT
            main == "right_shift" || main == "rshift" -> GLFW.GLFW_KEY_RIGHT_SHIFT
            main == "alt" || main == "left_alt" || main == "lalt" -> GLFW.GLFW_KEY_LEFT_ALT
            main == "right_alt" || main == "ralt" -> GLFW.GLFW_KEY_RIGHT_ALT
            main == "super" || main == "win" || main == "meta" || main == "cmd" -> GLFW.GLFW_KEY_LEFT_SUPER
            else -> GLFW.GLFW_KEY_UNKNOWN
        }
    }
}
