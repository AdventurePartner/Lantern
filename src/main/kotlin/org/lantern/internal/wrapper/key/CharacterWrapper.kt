package org.lantern.internal.wrapper.key

import com.google.gson.JsonObject

class CharacterWrapper(
    val resource: String,
    val width: Float,
    val height: Float,
    val wide: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    companion object {

        fun parse(obj: JsonObject): CharacterWrapper {
            val texture = obj.get("texture").asString
            val wrapper = CharacterWrapper(
                texture,
                obj.get("width").asFloat,
                obj.get("height").asFloat,
                obj.get("wide").asFloat,
                obj.get("offset-x")?.asFloat ?: 0f,
                obj.get("offset-y")?.asFloat ?: 0f
            )
            return wrapper
        }
    }
}
