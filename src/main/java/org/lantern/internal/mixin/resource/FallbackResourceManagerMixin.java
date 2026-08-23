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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Shadow
    private String namespace;

    /**
     * 核心修复：在 HEAD 注入点就检查并返回动态资源
     * 这确保在原始方法执行前就提供动态资源，避免原始方法返回错误结果
     */
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void lantern$getResourceHead(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!namespace.equals(location.getNamespace())) {
            return;
        }

        IResourceWrapper wrapper = ResourceHandler.INSTANCE.getResource(location);
        if (wrapper == null) {
            return;
        }

        try (InputStream is = wrapper.getResource()) {
            byte[] bytes = is.readAllBytes();
            cir.setReturnValue(Optional.of(new Resource(
                LanternVirtualPackResources.INSTANCE,
                () -> new ByteArrayInputStream(bytes)
            )));
            cir.cancel();
        } catch (Exception e) {
            Lantern.logger.error("[Lantern] Failed to provide dynamic resource: {}", e.getMessage());
        }
    }

    /**
     * 备用逻辑：如果 HEAD 注入点未能处理，在 RETURN 时再次检查动态注册资源。
     */
    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void lantern$getResourceReturn(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!namespace.equals(location.getNamespace())) {
            return;
        }

        if (cir.getReturnValue().isPresent()) {
            return;
        }

        // 优先查找动态注册的资源（如 model JSON）
        IResourceWrapper wrapper = ResourceHandler.INSTANCE.getResource(location);
        if (wrapper != null) {
            try {
                cir.setReturnValue(Optional.of(new Resource(
                    LanternVirtualPackResources.INSTANCE,
                    wrapper::getResource
                )));
                Lantern.logger.debug("[Lantern] Fallback provided dynamic resource: {}", location);
                return;
            } catch (Exception e) {
                Lantern.logger.error("[Lantern] Failed to provide fallback resource: {}", e.getMessage());
            }
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void lantern$listResources(String path, Predicate<ResourceLocation> predicate, CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        Map<ResourceLocation, Resource> merged = new HashMap<>(cir.getReturnValue());

        // 动态注册资源（对所有命名空间生效，包括加密包资源）
        Map<ResourceLocation, ? extends IResourceWrapper> dynamicResources = ResourceHandler.INSTANCE.listDynamicResources(namespace, path);
        dynamicResources.forEach((location, wrapper) -> {
            if (predicate.test(location)) {
                Resource resource = new Resource(
                    LanternVirtualPackResources.INSTANCE,
                    wrapper::getResource
                );
                // 动态资源优先级必须与 getResource HEAD 注入保持一致，确保本地包和加密包可覆盖图集资源。
                merged.put(location, resource);
            }
        });

        cir.setReturnValue(merged);
    }
}
