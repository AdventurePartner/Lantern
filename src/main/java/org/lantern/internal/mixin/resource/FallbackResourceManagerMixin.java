package org.lantern.internal.mixin.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.lantern.Lantern;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.internal.pack.LanternVirtualPackResources;
import org.lantern.internal.wrapper.resource.IResourceWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Shadow
    private String namespace;

    @Inject(method = "getResource", at = @At("HEAD"))
    private void lantern$getResourceHead(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (namespace.equals("lantern") && location.getPath().contains("custom")) {
            Lantern.logger.info("[Lantern-DEBUG] getResource HEAD: namespace={}, location={}, thread={}", namespace, location, Thread.currentThread().getName());
        }
    }

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void lantern$getResource(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!namespace.equals(location.getNamespace())) {
            return;
        }
        
        boolean isModel = location.getPath().startsWith("models/");
        boolean isTexture = location.getPath().startsWith("textures/");
        
        if (isModel || isTexture) {
            Lantern.logger.info("[Lantern-DEBUG] getResource: location={}, originalResult={}, isModel={}, isTexture={}", 
                location, cir.getReturnValue().isPresent(), isModel, isTexture);
        }

        IResourceWrapper wrapper = ResourceHandler.INSTANCE.getResource(location);
        if (wrapper == null) {
            if (isModel && location.getPath().contains("custom")) {
                Lantern.logger.warn("[Lantern-DEBUG] getResource: NO WRAPPER FOUND for {}, checking all registered keys...", location);
                Map<ResourceLocation, IResourceWrapper> all = ResourceHandler.INSTANCE.listDynamicResources("lantern", "models/");
                all.keySet().forEach(k -> Lantern.logger.info("[Lantern-DEBUG]   Registered: {}", k));
            }
            return;
        }

        if (isModel) {
            Lantern.logger.info("[Lantern-DEBUG] getResource HIT: location={}, wrapperClass={}", location, wrapper.getClass().getSimpleName());
            try {
                InputStream is = wrapper.getResource();
                byte[] bytes = is.readAllBytes();
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                Lantern.logger.info("[Lantern-DEBUG] Model JSON content for {}:\n{}", location, content);
                cir.setReturnValue(Optional.of(new Resource(
                    LanternVirtualPackResources.INSTANCE,
                    () -> new java.io.ByteArrayInputStream(bytes)
                )));
            } catch (Exception e) {
                Lantern.logger.error("[Lantern-DEBUG] Failed to read model content: {}", e.getMessage());
            }
            return;
        }

        if (cir.getReturnValue().isEmpty()) {
            Lantern.logger.info("[Lantern-DEBUG] getResource fallback for: {}", location);
            cir.setReturnValue(Optional.of(new Resource(
                LanternVirtualPackResources.INSTANCE,
                wrapper::getResource
            )));
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void lantern$listResources(String path, Predicate<ResourceLocation> predicate, CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        if (!"lantern".equals(namespace)) {
            return;
        }

        Map<ResourceLocation, Resource> merged = new HashMap<>(cir.getReturnValue());
        Map<ResourceLocation, ? extends IResourceWrapper> dynamicResources = ResourceHandler.INSTANCE.listDynamicResources(namespace, path);
        Lantern.logger.info("[Lantern-DEBUG] listResources: namespace={}, path={}, dynamicCount={}, originalCount={}", 
            namespace, path, dynamicResources.size(), merged.size());
        
        dynamicResources.forEach((location, wrapper) -> {
            boolean predicateMatch = predicate.test(location);
            Lantern.logger.info("[Lantern-DEBUG]   Checking location={}, predicateMatch={}", location, predicateMatch);
            if (predicateMatch) {
                Resource resource = new Resource(
                    LanternVirtualPackResources.INSTANCE,
                    wrapper::getResource
                );
                if (location.getPath().startsWith("models/")) {
                    merged.put(location, resource);
                } else {
                    merged.putIfAbsent(location, resource);
                }
            }
        });

        cir.setReturnValue(merged);
    }
}
