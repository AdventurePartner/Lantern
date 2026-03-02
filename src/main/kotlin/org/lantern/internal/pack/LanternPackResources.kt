package org.lantern.internal.pack

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.IoSupplier
import org.lantern.internal.handler.ResourceHandler
import java.io.InputStream
import java.nio.file.Path

class LanternPackResources(
    packLocationInfo: PackLocationInfo,
    path: Path
) : PathPackResources(packLocationInfo, path) {

    override fun getResource(
        packType: PackType,
        resource: ResourceLocation
    ): IoSupplier<InputStream?>? {
        if (packType == PackType.CLIENT_RESOURCES) {
            ResourceHandler.getResource(resource)?.let { return IoSupplier { it.getResource() } }
        }
        return super.getResource(packType, resource)
    }

    override fun listResources(
        packType: PackType,
        namespace: String,
        path: String,
        resourceOutput: PackResources.ResourceOutput
    ) {
        super.listResources(packType, namespace, path, resourceOutput)
        // 注入动态资源
        if (packType == PackType.CLIENT_RESOURCES) {
            ResourceHandler.listDynamicResources(namespace, path)
                .forEach { (k, v) -> resourceOutput.accept(k) { v.getResource() } }
        }
    }
}