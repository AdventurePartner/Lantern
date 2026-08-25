package org.lantern.internal.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

/**
 * Lantern 动态虚拟资源包。实时查询 ResourceHandler 内存表而非持有构造期快照：
 * GeckoLib 只在资源重载时扫描烘焙，若这里返回冻结快照，本轮重载中途写入的
 * 资源要等下一轮重载才可见（快照滞后一轮），表现为进服后模型丢失、需手动再重载。
 */
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

    public LanternDynamicPackResources(PackLocationInfo location) {
        super(location);
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
        IResourceWrapper resource = ResourceHandler.INSTANCE.getResource(location);
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
        ResourceHandler.INSTANCE.listDynamicResources(namespace, path)
            .forEach((location, resource) -> output.accept(location, resource::getResource));
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        return ResourceHandler.INSTANCE.resourceSnapshot().keySet().stream()
            .map(ResourceLocation::getNamespace)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void close() {
    }
}
