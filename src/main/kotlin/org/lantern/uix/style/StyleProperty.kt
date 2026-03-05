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
    BORDER_COLOR("border-color"),
    PLACEHOLDER_COLOR("placeholder-color"),
    OPACITY("opacity"),
    VISIBLE("visible"),

    // Layout properties
    DISPLAY("display"),
    POSITION("position"),
    FLEX_DIRECTION("flex-direction"),
    JUSTIFY_CONTENT("justify-content"),
    ALIGN_ITEMS("align-items"),
    GAP("gap"),
    ANCHOR("anchor"),

    // Padding
    PADDING("padding"),
    PADDING_TOP("padding-top"),
    PADDING_BOTTOM("padding-bottom"),
    PADDING_LEFT("padding-left"),
    PADDING_RIGHT("padding-right"),

    // Margin
    MARGIN("margin"),
    MARGIN_TOP("margin-top"),
    MARGIN_BOTTOM("margin-bottom"),
    MARGIN_LEFT("margin-left"),
    MARGIN_RIGHT("margin-right");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): StyleProperty? = BY_KEY[key]
    }
}
