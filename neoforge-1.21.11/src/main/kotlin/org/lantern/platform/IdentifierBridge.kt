package org.lantern.platform

import net.minecraft.resources.Identifier

object IdentifierBridge {
    @JvmStatic
    fun of(namespace: String, path: String): Identifier =
        Identifier.fromNamespaceAndPath(namespace, path)

    @JvmStatic
    fun parse(value: String): Identifier = Identifier.parse(value)
}
