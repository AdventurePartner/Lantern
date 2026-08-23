package org.lantern.internal.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.internal.wrapper.resource.IResourceWrapper;

public final class LanternDynamicPackResources extends AbstractPackResources {
    private static final byte[] PACK_METADATA = """
        {
          "pack": {
            "description": "Lantern dynamic resources",
            "min_format": [69, 0],
            "max_format": [69, 0]
          }
        }
        """.getBytes(StandardCharsets.UTF_8);

    private final Map<ResourceLocation, IResourceWrapper> resources;

    public LanternDynamicPackResources(PackLocationInfo location) {
        super(location);
        this.resources = ResourceHandler.INSTANCE.resourceSnapshot();
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && PACK_META.equals(elements[0])) {
            return () -> new ByteArrayInputStream(PACK_METADATA);
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        IResourceWrapper resource = resources.get(location);
        return resource == null ? null : resource::getResource;
    }

    @Override
    public void listResources(
        PackType type,
        String namespace,
        String path,
        PackResources.ResourceOutput output
    ) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        resources.forEach((location, resource) -> {
            if (location.getNamespace().equals(namespace) && location.getPath().startsWith(path)) {
                output.accept(location, resource::getResource);
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        return resources.keySet().stream()
            .map(ResourceLocation::getNamespace)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void close() {
    }
}
