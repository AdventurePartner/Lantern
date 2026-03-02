package org.lantern.internal.util

import org.lwjgl.glfw.GLFW

object KeyUtils {
    private val keyMap = mapOf(
        GLFW.GLFW_KEY_ENTER to "Enter",
        GLFW.GLFW_KEY_ESCAPE to "Escape",
        GLFW.GLFW_KEY_TAB to "Tab",
        GLFW.GLFW_KEY_SPACE to "Space",
        GLFW.GLFW_KEY_BACKSPACE to "Backspace",
        GLFW.GLFW_KEY_UP to "Up",
        GLFW.GLFW_KEY_DOWN to "Down",
        GLFW.GLFW_KEY_LEFT to "Left",
        GLFW.GLFW_KEY_RIGHT to "Right",
        GLFW.GLFW_KEY_DELETE to "Delete",
        GLFW.GLFW_KEY_INSERT to "Insert",
        GLFW.GLFW_KEY_HOME to "Home",
        GLFW.GLFW_KEY_END to "End",
        GLFW.GLFW_KEY_F1 to "F1",
        GLFW.GLFW_KEY_F2 to "F2",
        GLFW.GLFW_KEY_F3 to "F3",
        GLFW.GLFW_KEY_F4 to "F4",
        GLFW.GLFW_KEY_F5 to "F5",
        GLFW.GLFW_KEY_F6 to "F6",
        GLFW.GLFW_KEY_F7 to "F7",
        GLFW.GLFW_KEY_F8 to "F8",
        GLFW.GLFW_KEY_F9 to "F9",
        GLFW.GLFW_KEY_F10 to "F10",
        GLFW.GLFW_KEY_F11 to "F11",
        GLFW.GLFW_KEY_F12 to "F12"
    )
    private val modifierKeys = mapOf(
        GLFW.GLFW_KEY_LEFT_CONTROL to "Ctrl",
        GLFW.GLFW_KEY_RIGHT_CONTROL to "Ctrl",
        GLFW.GLFW_KEY_LEFT_SHIFT to "Shift",
        GLFW.GLFW_KEY_RIGHT_SHIFT to "Shift",
        GLFW.GLFW_KEY_LEFT_ALT to "Alt",
        GLFW.GLFW_KEY_RIGHT_ALT to "Alt",
        GLFW.GLFW_KEY_LEFT_SUPER to "Super",
        GLFW.GLFW_KEY_RIGHT_SUPER to "Super"
    )

    fun getKeyName(key: Int, scanCode: Int, modifiers: Int): String {
        val modifierList = mutableListOf<String>()
        if (modifiers and GLFW.GLFW_MOD_CONTROL != 0) modifierList.add("Ctrl")
        if (modifiers and GLFW.GLFW_MOD_SHIFT != 0) modifierList.add("Shift")
        if (modifiers and GLFW.GLFW_MOD_ALT != 0) modifierList.add("Alt")
        if (modifiers and GLFW.GLFW_MOD_SUPER != 0) modifierList.add("Super")

        // 获取主键名
        val keyName = when (key) {
            in modifierKeys.keys -> modifierKeys[key]!!
            else -> keyMap[key] ?: GLFW.glfwGetKeyName(key, scanCode)?.uppercase() ?: "Unknown"
        }

        // 避免单独按修饰键显示重复
        return if (keyName in listOf("Ctrl", "Shift", "Alt", "Super") && modifierList.size == 1) {
            keyName
        } else if (modifierList.isEmpty()) {
            keyName
        } else {
            modifierList.joinToString("+") + "+$keyName"
        }
    }
}