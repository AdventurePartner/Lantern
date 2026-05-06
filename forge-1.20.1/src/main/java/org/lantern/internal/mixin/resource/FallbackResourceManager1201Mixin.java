package org.lantern.internal.mixin.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.lantern.Lantern;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.internal.pack.LanternVirtualPackResources1201;
import org.lantern.internal.wrapper.resource.IResourceWrapper;
import org.lantern.platform.ClientPathBridge;
import org.lantern.platform.IdentifierBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManager1201Mixin {
    @Shadow
    private String namespace;

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void lantern$getResourceHead(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!namespace.equals(location.getNamespace())) {
            return;
        }

        IResourceWrapper wrapper = ResourceHandler.INSTANCE.getResource(location);
        if (wrapper == null) {
            return;
        }

        try {
            InputStream is = wrapper.getResource();
            byte[] bytes = is.readAllBytes();
            cir.setReturnValue(Optional.of(new Resource(
                LanternVirtualPackResources1201.INSTANCE,
                () -> new ByteArrayInputStream(bytes)
            )));
        } catch (Exception e) {
            Lantern.logger.error("[Lantern] Failed to provide dynamic resource: {}", e.getMessage());
        }
    }

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void lantern$getResourceReturn(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!namespace.equals(location.getNamespace())) {
            return;
        }

        if (cir.getReturnValue().isPresent()) {
            return;
        }

        IResourceWrapper wrapper = ResourceHandler.INSTANCE.getResource(location);
        if (wrapper != null) {
            try {
                cir.setReturnValue(Optional.of(new Resource(
                    LanternVirtualPackResources1201.INSTANCE,
                    wrapper::getResource
                )));
                Lantern.logger.debug("[Lantern] Fallback provided dynamic resource: {}", location);
                return;
            } catch (Exception e) {
                Lantern.logger.error("[Lantern] Failed to provide fallback resource: {}", e.getMessage());
            }
        }

        try {
            Path localFile = ClientPathBridge.gameDir()
                .resolve("resourcePacks")
                .resolve("LanternPackLocal")
                .resolve("assets")
                .resolve(location.getNamespace())
                .resolve(location.getPath());
            if (Files.exists(localFile)) {
                cir.setReturnValue(Optional.of(new Resource(
                    LanternVirtualPackResources1201.INSTANCE,
                    () -> Files.newInputStream(localFile)
                )));
            }
        } catch (Exception e) {
            Lantern.logger.error("[Lantern] Failed to read from LanternPackLocal: {}", e.getMessage());
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void lantern$listResources(
        String path,
        Predicate<ResourceLocation> predicate,
        CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir
    ) {
        Map<ResourceLocation, Resource> merged = new HashMap<>(cir.getReturnValue());

        Map<ResourceLocation, ? extends IResourceWrapper> dynamicResources =
            ResourceHandler.INSTANCE.listDynamicResources(namespace, path);
        dynamicResources.forEach((location, wrapper) -> {
            if (predicate.test(location)) {
                Resource resource = new Resource(
                    LanternVirtualPackResources1201.INSTANCE,
                    wrapper::getResource
                );
                if (location.getPath().startsWith("models/")) {
                    merged.put(location, resource);
                } else {
                    merged.putIfAbsent(location, resource);
                }
            }
        });

        if ("lantern".equals(namespace)) {
            try {
                Path localNsPath = ClientPathBridge.gameDir()
                    .resolve("resourcePacks")
                    .resolve("LanternPackLocal")
                    .resolve("assets")
                    .resolve("lantern");
                Path searchPath = localNsPath.resolve(path);
                if (Files.exists(searchPath)) {
                    Files.walk(searchPath)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                String rel = localNsPath.relativize(file).toString().replace('\\', '/');
                                ResourceLocation rl = IdentifierBridge.of("lantern", rel);
                                if (predicate.test(rl)) {
                                    merged.putIfAbsent(rl, new Resource(
                                        LanternVirtualPackResources1201.INSTANCE,
                                        () -> Files.newInputStream(file)
                                    ));
                                }
                            } catch (Exception fileEx) {
                                Lantern.logger.debug(
                                    "[Lantern] Skipping invalid resource file (illegal chars in name): {}",
                                    file.getFileName()
                                );
                            }
                        });
                }
            } catch (Exception e) {
                Lantern.logger.warn("[Lantern] Failed to list LanternPackLocal/{}: {}", path, e.getMessage());
            }
        }

        cir.setReturnValue(merged);
    }
}
