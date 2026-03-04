package org.lantern.uix.input

import org.lantern.internal.action.UiActionHandler
import org.lantern.uix.widget.input.InputWidgetImpl
import org.lwjgl.glfw.GLFW

object FocusManager {

    private var current: InputWidgetImpl? = null

    val focused: InputWidgetImpl? get() = current

    fun focus(widget: InputWidgetImpl) {
        current?.focused = false
        current = widget
        widget.focused = true
        widget.moveCursorEnd()
    }

    fun blur() {
        current?.focused = false
        current = null
    }

    fun handleChar(c: Char): Boolean {
        val w = current ?: return false
        w.insertChar(c)
        return true
    }

    fun handleKey(keyCode: Int): Boolean {
        val w = current ?: return false
        return when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE                       -> { w.backspace(); true }
            GLFW.GLFW_KEY_DELETE                          -> { w.deleteForward(); true }
            GLFW.GLFW_KEY_LEFT                            -> { w.moveCursorLeft(); true }
            GLFW.GLFW_KEY_RIGHT                           -> { w.moveCursorRight(); true }
            GLFW.GLFW_KEY_HOME                            -> { w.moveCursorHome(); true }
            GLFW.GLFW_KEY_END                             -> { w.moveCursorEnd(); true }
            GLFW.GLFW_KEY_ESCAPE                          -> { blur(); true }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                val action = w.onChange
                blur()
                if (action.isNotBlank()) UiActionHandler.execute(action)
                true
            }
            else -> false
        }
    }
}
