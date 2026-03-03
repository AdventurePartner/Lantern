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

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

        try {
            InputStream is = wrapper.getResource();
            byte[] bytes = is.readAllBytes();
            cir.setReturnValue(Optional.of(new Resource(
                LanternVirtualPackResources.INSTANCE,
                () -> new ByteArrayInputStream(bytes)
            )));
        } catch (Exception e) {
            Lantern.logger.error("[Lantern] Failed to provide dynamic resource: {}", e.getMessage());
        }
    }

    /**
     * 备用逻辑：如果 HEAD 注入点未能处理，在 RETURN 时再次检查。
     * 优先级：动态注册资源（model JSON）> LanternPackLocal 本地文件夹（纹理 PNG 等）
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

        // 从 LanternPackLocal 本地文件夹读取（主要用于纹理 PNG）
        try {
            Path localFile = FabricLoader.getInstance().getGameDir()
                .resolve("resourcePacks")
                .resolve("LanternPackLocal")
                .resolve("assets")
                .resolve(location.getNamespace())
                .resolve(location.getPath());
            if (Files.exists(localFile)) {
                cir.setReturnValue(Optional.of(new Resource(
                    LanternVirtualPackResources.INSTANCE,
                    () -> Files.newInputStream(localFile)
                )));
            }
        } catch (Exception e) {
            Lantern.logger.error("[Lantern] Failed to read from LanternPackLocal: {}", e.getMessage());
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void lantern$listResources(String path, Predicate<ResourceLocation> predicate, CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        if (!"lantern".equals(namespace)) {
            return;
        }

        Map<ResourceLocation, Resource> merged = new HashMap<>(cir.getReturnValue());

        // 动态注册资源（如 model JSON，保留兼容）
        Map<ResourceLocation, ? extends IResourceWrapper> dynamicResources = ResourceHandler.INSTANCE.listDynamicResources(namespace, path);
        dynamicResources.forEach((location, wrapper) -> {
            if (predicate.test(location)) {
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

        // 遍历 LanternPackLocal 本地文件夹，将其中的资源（主要是纹理 PNG）暴露给图集缝合器
        // 当 vanilla 的 atlases/blocks.json 中 DirectoryLister 调用 listResources("textures/item",...) 时，
        // 此处会将 LanternPackLocal/assets/lantern/textures/item/*.png 加入返回列表，从而进入图集
        try {
            Path localNsPath = FabricLoader.getInstance().getGameDir()
                .resolve("resourcePacks")
                .resolve("LanternPackLocal")
                .resolve("assets")
                .resolve("lantern");
            Path searchPath = localNsPath.resolve(path);
        if (Files.exists(searchPath)) {
                Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        // 逐文件捕获异常：含空格、括号等非法字符的文件名会导致
                        // ResourceLocation 创建失败，跳过该文件而不中断整个遍历
                        try {
                            String rel = localNsPath.relativize(file).toString().replace('\\', '/');
                            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("lantern", rel);
                            if (predicate.test(rl)) {
                                merged.putIfAbsent(rl, new Resource(
                                    LanternVirtualPackResources.INSTANCE,
                                    () -> Files.newInputStream(file)
                                ));
                            }
                        } catch (Exception fileEx) {
                            Lantern.logger.debug("[Lantern] Skipping invalid resource file (illegal chars in name): {}", file.getFileName());
                        }
                    });
            }
        } catch (Exception e) {
            Lantern.logger.warn("[Lantern] Failed to list LanternPackLocal/{}: {}", path, e.getMessage());
        }

        cir.setReturnValue(merged);
    }
}
