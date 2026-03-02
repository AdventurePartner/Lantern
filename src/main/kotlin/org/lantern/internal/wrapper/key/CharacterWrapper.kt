package org.lantern.internal.wrapper.key

import com.google.gson.JsonObject

class CharacterWrapper(
    val resource: String,
    val width: Int,
    val height: Int,
    val wide: Int
) {
    companion object {

        fun parse(obj: JsonObject): CharacterWrapper {
            val texture = obj.get("texture").asString
            val wrapper = CharacterWrapper(
                texture,
                obj.get("width").asInt,
                obj.get("height").asInt,
                obj.get("wide").asInt
            )
            return wrapper
        }
    }
}