package org.lantern.internal.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

object JsonUtils {
    private val gson = GsonBuilder().create()

    fun fromString(string: String): JsonObject {
        return gson.fromJson(string, JsonObject::class.java)
    }
}