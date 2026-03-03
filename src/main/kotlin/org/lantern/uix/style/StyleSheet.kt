package org.lantern.uix.style

import com.google.gson.JsonObject

/**
 * Named collection of [StyleRule]s, equivalent to a CSS stylesheet.
 * Rules are looked up by name (e.g. "btn-primary") and can be merged with inline styles.
 */
class StyleSheet(private val rules: Map<String, StyleRule> = emptyMap()) {

    fun get(name: String): StyleRule = rules[name] ?: StyleRule.EMPTY

    companion object {
        val EMPTY = StyleSheet()

        fun fromJson(stylesObj: JsonObject): StyleSheet {
            val rules = mutableMapOf<String, StyleRule>()
            stylesObj.entrySet().forEach { (name, value) ->
                if (value.isJsonObject) {
                    rules[name] = StyleRule.fromJson(value.asJsonObject)
                }
            }
            return StyleSheet(rules)
        }
    }
}
