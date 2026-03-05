package org.lantern.uix.enums

enum class Display(val key: String) {
    FLEX("flex"),
    NONE("none");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): Display? = BY_KEY[key]
    }
}
