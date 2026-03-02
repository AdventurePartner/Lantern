package org.lantern.internal.wrapper.resource

import net.minecraft.resources.ResourceLocation
import java.io.InputStream

interface IResourceWrapper {

    fun getResourceLocation(): ResourceLocation

    fun getResource(): InputStream
}