package org.lantern.uix.enums

enum class JustifyContent(val key: String) {
    START("start"),
    CENTER("center"),
    END("end"),
    SPACE_BETWEEN("space-between"),
    SPACE_EVENLY("space-evenly");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): JustifyContent? = BY_KEY[key]
    }
}
