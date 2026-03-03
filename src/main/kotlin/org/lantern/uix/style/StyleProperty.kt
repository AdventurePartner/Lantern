package org.lantern.uix.style

enum class StyleProperty(val key: String) {
    X("x"),
    Y("y"),
    WIDTH("width"),
    HEIGHT("height"),
    COLOR("color"),
    BACKGROUND("background"),
    FONT_SIZE("font-size"),
    BORDER_RADIUS("border-radius"),
    OPACITY("opacity"),
    VISIBLE("visible");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): StyleProperty? = BY_KEY[key]
    }
}
