package org.lantern.internal.mixin.model;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.lantern.internal.handler.ResourceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiFunction;

@Mixin(ModelBakery.class)
public abstract class ModelBakery1201Mixin {

    @Shadow
    private void loadTopLevel(ModelResourceLocation modelResourceLocation) {
    }

    @Inject(method = "bakeModels", at = @At("HEAD"))
    private void lantern$bakeModelsHead(BiFunction<?, ?, ?> textureGetter, CallbackInfo ci) {
        Map<Integer, ModelResourceLocation> icons = ResourceHandler.INSTANCE.getItemCustomIcons();
        icons.forEach((customModelData, modelLoc) -> loadTopLevel(modelLoc));

        Map<Integer, ModelResourceLocation> blocks = ResourceHandler.INSTANCE.getBlockCustomModels();
        blocks.forEach((variation, modelLoc) -> loadTopLevel(modelLoc));
    }
}
