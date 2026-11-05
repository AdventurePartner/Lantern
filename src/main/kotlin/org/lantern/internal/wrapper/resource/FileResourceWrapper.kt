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

    /** 扫描后文件可能被改名/删除，与原版 PathPackResources 一致：给出 supplier 前先查存在性 */
    override fun isAvailable(): Boolean = Files.isRegularFile(path)
}
