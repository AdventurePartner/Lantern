package org.lantern.uix.widget.input

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.widget.BaseWidget

class InputWidgetImpl(
    var value: String = "",
    var placeholder: String = "",
    var maxLength: Int = 256,
    var onChange: String = ""
) : BaseWidget() {

    override val widgetType: String = "input"

    var focused: Boolean = false
    var cursorPos: Int = 0
    var viewStart: Int = 0

    fun insertChar(c: Char) {
        if (value.length >= maxLength) return
        value = value.substring(0, cursorPos) + c + value.substring(cursorPos)
        cursorPos++
    }

    fun backspace() {
        if (cursorPos > 0) {
            value = value.substring(0, cursorPos - 1) + value.substring(cursorPos)
            cursorPos--
        }
    }

    fun deleteForward() {
        if (cursorPos < value.length) {
            value = value.substring(0, cursorPos) + value.substring(cursorPos + 1)
        }
    }

    fun moveCursorLeft() { if (cursorPos > 0) cursorPos-- }
    fun moveCursorRight() { if (cursorPos < value.length) cursorPos++ }
    fun moveCursorHome() { cursorPos = 0 }
    fun moveCursorEnd() { cursorPos = value.length }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
    }
}
