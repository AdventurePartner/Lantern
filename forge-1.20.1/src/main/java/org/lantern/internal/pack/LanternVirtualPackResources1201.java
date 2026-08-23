package org.lantern.internal.pack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

public final class LanternVirtualPackResources1201 implements PackResources {
    public static final LanternVirtualPackResources1201 INSTANCE = new LanternVirtualPackResources1201();

    private LanternVirtualPackResources1201() {}

    @Override
    public IoSupplier<InputStream> getRootResource(String... strings) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {}

    @Override
    public Set<String> getNamespaces(PackType packType) {
        return Collections.singleton("lantern");
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metadataSectionSerializer) {
        return null;
    }

    @Override
    public String packId() {
        return "lantern_virtual_dynamic";
    }

    @Override
    public void close() {}
}
