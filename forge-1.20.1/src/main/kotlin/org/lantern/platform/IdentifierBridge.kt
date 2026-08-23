package org.lantern.platform

import net.minecraft.resources.ResourceLocation

object IdentifierBridge {
    @JvmStatic
    fun of(namespace: String, path: String): ResourceLocation =
        ResourceLocation(namespace, path)

    @JvmStatic
    fun parse(value: String): ResourceLocation = ResourceLocation(value)
}

