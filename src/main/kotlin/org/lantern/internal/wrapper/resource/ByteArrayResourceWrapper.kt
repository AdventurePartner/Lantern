package org.lantern.internal.wrapper.resource

import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.io.InputStream

class ByteArrayResourceWrapper(
    private val resourceLocation: ResourceLocation,
    private val data: ByteArray
) : IResourceWrapper {

    override fun getResourceLocation(): ResourceLocation = resourceLocation

    override fun getResource(): InputStream = ByteArrayInputStream(data)
}
