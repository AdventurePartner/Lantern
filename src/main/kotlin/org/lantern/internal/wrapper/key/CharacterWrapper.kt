package org.lantern.internal.wrapper.key

import com.google.gson.JsonObject

class CharacterWrapper(
    val resource: String,
    val width: Float,
    val height: Float,
    val wide: Float
) {
    companion object {

        fun parse(obj: JsonObject): CharacterWrapper {
            val texture = obj.get("texture").asString
            val wrapper = CharacterWrapper(
                texture,
                obj.get("width").asFloat,
                obj.get("height").asFloat,
                obj.get("wide").asFloat
            )
            return wrapper
        }
    }
}