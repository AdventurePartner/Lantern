package org.lantern.uix.enums

enum class AlignItems(val key: String) {
    START("start"),
    CENTER("center"),
    END("end"),
    STRETCH("stretch");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): AlignItems? = BY_KEY[key]
    }
}
