package org.lantern.uix.event

data class MouseEvent(
    val x: Int,
    val y: Int,
    val button: Int = 0,
    var consumed: Boolean = false
) {
    fun consume() { consumed = true }
}
