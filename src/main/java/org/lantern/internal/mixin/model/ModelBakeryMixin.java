package org.lantern.internal.mixin.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.lantern.Lantern;
import org.lantern.internal.handler.ResourceHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {

    @Shadow
    abstract void loadItemModelAndDependencies(ResourceLocation arg);

    @Shadow
    abstract UnbakedModel getModel(ResourceLocation arg);

    @Final
    @Shadow
    private Map<ModelResourceLocation, UnbakedModel> topLevelModels;

    @Final
    @Shadow
    private Map<ResourceLocation, UnbakedModel> unbakedCache;

    @Final
    @Shadow
    private UnbakedModel missingModel;

    @Inject(method = "getModel", at = @At("HEAD"))
    private void inject$getModelHead(ResourceLocation location, CallbackInfoReturnable<UnbakedModel> cir) {
        if (location.getNamespace().equals("lantern") && location.getPath().contains("custom")) {
            boolean inCache = unbakedCache.containsKey(location);
            UnbakedModel cached = unbakedCache.get(location);
            boolean isMissing = cached == this.missingModel;
            Lantern.logger.info("[Lantern-DEBUG] getModel HEAD: location={}, inUnbakedCache={}, isMissing={}", location, inCache, isMissing);
        }
    }

    @Inject(method = "loadItemModelAndDependencies", at = @At("HEAD"))
    private void inject$loadItemModelAndDependencies(ResourceLocation location, CallbackInfo ci) {
        if (location.getNamespace().equals("lantern")) {
            Lantern.logger.info("[Lantern-DEBUG] loadItemModelAndDependencies HEAD: location={}", location);
            
            ResourceLocation modelPath = ResourceLocation.fromNamespaceAndPath(
                location.getNamespace(), 
                "models/" + location.getPath() + ".json"
            );
            boolean resourceExists = Minecraft.getInstance().getResourceManager().getResource(modelPath).isPresent();
            Lantern.logger.info("[Lantern-DEBUG] Checking resource path: {}, exists={}", modelPath, resourceExists);
        }
    }

    @Inject(method = "loadItemModelAndDependencies", at = @At("RETURN"))
    private void inject$loadItemModelAndDependenciesReturn(ResourceLocation location, CallbackInfo ci) {
        if (location.getNamespace().equals("lantern") && location.getPath().contains("custom")) {
            UnbakedModel model = unbakedCache.get(location);
            boolean isMissing = model == this.missingModel;
            Lantern.logger.info("[Lantern-DEBUG] loadItemModelAndDependencies RETURN: location={}, cachedModel={}, isMissing={}", 
                location, model != null ? model.getClass().getSimpleName() : "null", isMissing);
        }
    }

    @Inject(method = "bakeModels", at = @At("HEAD"))
    private void inject$bakeModelsHead(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        Map<Integer, ModelResourceLocation> icons = ResourceHandler.INSTANCE.getItemCustomIcons();
        int size = icons.size();
        Lantern.logger.info("[Lantern-DEBUG] ModelBakery.bakeModels HEAD: itemCustomIcons size: {}", size);
        
        if (size == 0) {
            Lantern.logger.warn("[Lantern-DEBUG] No custom item icons registered! Check network packet handling.");
            return;
        }
        
        icons.forEach((customModelData, modelLoc) -> {
            ResourceLocation modelId = modelLoc.id();
            // loadItemModelAndDependencies 会自动添加 item/ 前缀
            // 模型会被缓存到 lantern:item/custom_wrapper
            ResourceLocation cacheKey = ResourceLocation.fromNamespaceAndPath(
                modelId.getNamespace(), "item/" + modelId.getPath()
            );
            Lantern.logger.info("[Lantern-DEBUG] Processing: customModelData={}, modelLoc={}, modelId={}, cacheKey={}", 
                customModelData, modelLoc, modelId, cacheKey);
            
            if (topLevelModels.containsKey(modelLoc)) {
                Lantern.logger.info("[Lantern-DEBUG] Model {} already in topLevelModels", modelLoc);
                return;
            }
            
            UnbakedModel cached = unbakedCache.get(cacheKey);
            Lantern.logger.info("[Lantern-DEBUG] unbakedCache lookup: cacheKey={}, found={}, isMissing={}", 
                cacheKey, cached != null, cached == missingModel);
            
            if (cached == null || cached == missingModel) {
                Lantern.logger.info("[Lantern-DEBUG] Calling loadItemModelAndDependencies for: {}", modelId);
                this.loadItemModelAndDependencies(modelId);
                cached = unbakedCache.get(cacheKey);
                Lantern.logger.info("[Lantern-DEBUG] After load: cacheKey={}, cached={}, isMissing={}", 
                    cacheKey, cached != null, cached == missingModel);
            }
            
            if (cached != null && cached != missingModel) {
                topLevelModels.put(modelLoc, cached);
                Lantern.logger.info("[Lantern-DEBUG] SUCCESS: Added {} to topLevelModels", modelLoc);
            } else {
                Lantern.logger.error("[Lantern-DEBUG] FAILED: Could not load model for: modelLoc={}, cacheKey={}", modelLoc, cacheKey);
            }
        });
    }

    @Inject(method = "bakeModels", at = @At("RETURN"))
    private void inject$bakeModelsReturn(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        Lantern.logger.info("[Lantern-DEBUG] ModelBakery.bakeModels RETURN: topLevelModels size={}", topLevelModels.size());
        topLevelModels.keySet().stream()
            .filter(k -> k.id().getNamespace().equals("lantern"))
            .forEach(k -> Lantern.logger.info("[Lantern-DEBUG]   topLevelModel: {}", k));
    }
}
