package org.lantern.internal.wrapper.resource

import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

class FileResourceWrapper(
    private val resourceLocation: ResourceLocation,
    private val path: Path
) : IResourceWrapper {

    override fun getResourceLocation(): ResourceLocation = resourceLocation

    override fun getResource(): InputStream = Files.newInputStream(path)
}
