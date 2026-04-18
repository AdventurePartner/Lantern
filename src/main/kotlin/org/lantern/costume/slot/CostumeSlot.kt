package org.lantern.costume.slot

enum class CostumeSlot {
    FULL_BODY,
    BACK,
    TAIL,
    HEAD,
    EFFECT;

    companion object {
        fun fromString(value: String): CostumeSlot =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FULL_BODY
    }
}
