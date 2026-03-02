package org.lantern.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

object JsonUtil {
    private val gson = GsonBuilder().create()

    fun toJson(obj: JsonObject): String {
        return gson.toJson(obj)
    }
}