package org.lantern.uix.enums

enum class FlexDirection(val key: String) {
    ROW("row"),
    COLUMN("column");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun fromKey(key: String): FlexDirection? = BY_KEY[key]
    }
}
