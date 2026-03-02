package org.lantern.internal.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public final class LanternVirtualPackResources implements PackResources {
    public static final LanternVirtualPackResources INSTANCE = new LanternVirtualPackResources();

    private static final PackLocationInfo INFO = new PackLocationInfo(
        "lantern_virtual_dynamic",
        Component.literal("Lantern Dynamic Resources"),
        PackSource.BUILT_IN,
        Optional.empty()
    );

    private LanternVirtualPackResources() {}

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
    public PackLocationInfo location() {
        return INFO;
    }

    @Override
    public void close() {}
}
