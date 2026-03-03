package org.lantern.uix.style

import com.google.gson.JsonObject

/**
 * Immutable set of resolved style properties for a single widget.
 * Use [merge] to layer one rule on top of another (other wins on conflict).
 */
data class StyleRule(val properties: Map<StyleProperty, Any> = emptyMap()) {

    fun get(property: StyleProperty): Any? = properties[property]

    inline fun <reified T> getAs(property: StyleProperty): T? = get(property) as? T

    fun getInt(property: StyleProperty, default: Int = 0): Int =
        (get(property) as? Number)?.toInt() ?: default

    fun getFloat(property: StyleProperty, default: Float = 0f): Float =
        (get(property) as? Number)?.toFloat() ?: default

    fun getString(property: StyleProperty, default: String = ""): String =
        get(property) as? String ?: default

    fun getBoolean(property: StyleProperty, default: Boolean = true): Boolean =
        get(property) as? Boolean ?: default

    /** Returns a new rule with [other]'s properties overriding this rule's. */
    fun merge(other: StyleRule): StyleRule =
        StyleRule(properties + other.properties)

    companion object {
        val EMPTY = StyleRule()

        fun fromJson(obj: JsonObject): StyleRule {
            val map = mutableMapOf<StyleProperty, Any>()
            obj.entrySet().forEach { (key, value) ->
                val prop = StyleProperty.fromKey(key) ?: return@forEach
                val parsed: Any = when {
                    value.isJsonPrimitive -> {
                        val prim = value.asJsonPrimitive
                        when {
                            prim.isBoolean -> prim.asBoolean
                            prim.isNumber -> prim.asDouble
                            else -> prim.asString
                        }
                    }
                    else -> return@forEach
                }
                map[prop] = parsed
            }
            return StyleRule(map)
        }
    }
}
