package org.lantern.internal.mixin.model;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.lantern.internal.handler.ResourceHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {

    @Final
    @Shadow
    private Map<ModelResourceLocation, UnbakedModel> topLevelModels;
    @Final
    @Shadow
    private Map<ResourceLocation, UnbakedModel> unbakedCache;
    @Final
    @Shadow
    private UnbakedModel missingModel;
    @Shadow
    abstract void loadItemModelAndDependencies(ResourceLocation arg);

    @Inject(method = "bakeModels", at = @At("HEAD"))
    private void inject$bakeModelsHead(ModelBakery.TextureGetter textureGetter, CallbackInfo ci) {
        Map<Integer, ModelResourceLocation> icons = ResourceHandler.INSTANCE.getItemCustomIcons();
        if (icons.isEmpty()) {
            return;
        }
        icons.forEach((customModelData, modelLoc) -> {
            ResourceLocation modelId = modelLoc.id();
            ResourceLocation cacheKey = ResourceLocation.fromNamespaceAndPath(
                modelId.getNamespace(), "item/" + modelId.getPath()
            );
            if (topLevelModels.containsKey(modelLoc)) {
                return;
            }
            UnbakedModel cached = unbakedCache.get(cacheKey);
            if (cached == null || cached == missingModel) {
                this.loadItemModelAndDependencies(modelId);
                cached = unbakedCache.get(cacheKey);
            }
            if (cached != null && cached != missingModel) {
                topLevelModels.put(modelLoc, cached);
            }
        });
    }
}
