package org.lantern.internal.mixin.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.lantern.Lantern;
import org.lantern.internal.handler.ResourceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void lantern$getModel(ItemStack itemStack,
                                  Level level,
                                  LivingEntity entity,
                                  int seed,
                                  CallbackInfoReturnable<BakedModel> cir) {
        Integer customData = lantern$getCustomModelDataValue(itemStack);
        if (customData == null) {
            return;
        }

        String identifier = ResourceHandler.INSTANCE.getItemIcon(customData);
        if (identifier == null) {
            Lantern.logger.info("[Lantern-DEBUG] ItemRenderer: customModelData={} found, but no identifier mapped", customData);
            return;
        }

        Lantern.logger.info("[Lantern-DEBUG] ItemRenderer: customModelData={}, identifier={}", customData, identifier);

        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        Object[] resolved = lantern$resolveModel(modelManager, identifier);
        if (resolved == null) {
            Lantern.logger.warn("[Lantern-DEBUG] ItemRenderer: FAILED to resolve model for identifier='{}', checking all registered...", identifier);
            Map<Integer, ModelResourceLocation> allIcons = ResourceHandler.INSTANCE.getItemCustomIcons();
            allIcons.forEach((k, v) -> Lantern.logger.info("[Lantern-DEBUG]   Registered: customModelData={} -> modelLoc={}", k, v));
            
            // 尝试直接从 ModelManager 获取
            lantern$debugModelManager(modelManager, identifier);
            return;
        }

        ModelResourceLocation resolvedLocation = (ModelResourceLocation) resolved[0];
        BakedModel model = (BakedModel) resolved[1];
        int quadCount = model.getQuads(null, null, RandomSource.create(0L)).size();
        ResourceLocation particle = model.getParticleIcon().contents().name();
        boolean isMissingTexture = particle.equals(MissingTextureAtlasSprite.getLocation());
        
        Lantern.logger.info(
            "[Lantern-DEBUG] ItemRenderer SUCCESS: customModelData={}, model={}, quads={}, particle={}, isMissingTexture={}",
            customData, resolvedLocation, quadCount, particle, isMissingTexture
        );
        
        if (isMissingTexture) {
            Lantern.logger.warn("[Lantern-DEBUG] Model found but using missing texture! This indicates texture loading failed.");
        }
        
        cir.setReturnValue(model);
        cir.cancel();
    }

    @Unique
    private void lantern$debugModelManager(ModelManager modelManager, String identifier) {
        String normalized = identifier.startsWith("item/") ? identifier.substring("item/".length()) : identifier;
        ModelResourceLocation[] candidates = new ModelResourceLocation[] {
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, normalized)),
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "item/" + normalized)),
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "item/" + identifier))
        };

        BakedModel missingModel = modelManager.getMissingModel();
        for (ModelResourceLocation candidate : candidates) {
            BakedModel model = modelManager.getModel(candidate);
            boolean isMissing = model == missingModel;
            ResourceLocation particle = model.getParticleIcon().contents().name();
            Lantern.logger.info("[Lantern-DEBUG]   Candidate: {}, isMissing={}, particle={}", candidate, isMissing, particle);
        }
    }

    @Unique
    private Integer lantern$getCustomModelDataValue(ItemStack stack) {
        CustomModelData modern = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (modern != null) {
            return modern.value();
        }

        CustomData legacy = stack.get(DataComponents.CUSTOM_DATA);
        if (legacy == null) {
            return null;
        }
        if (!legacy.contains("CustomModelData")) {
            return null;
        }
        if (!legacy.copyTag().contains("CustomModelData", Tag.TAG_INT)) {
            return null;
        }
        return legacy.copyTag().getInt("CustomModelData");
    }

    @Unique
    private Object[] lantern$resolveModel(ModelManager modelManager, String identifier) {
        String normalized = identifier.startsWith("item/") ? identifier.substring("item/".length()) : identifier;
        ModelResourceLocation[] candidates = new ModelResourceLocation[] {
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, normalized)),
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "item/" + normalized))
        };

        Object[] fallback = null;
        for (ModelResourceLocation candidate : candidates) {
            BakedModel model = modelManager.getModel(candidate);
            if (model == modelManager.getMissingModel()) {
                Lantern.logger.info("[Lantern-DEBUG]   Candidate {} is missing model, skipping", candidate);
                continue;
            }
            ResourceLocation particle = model.getParticleIcon().contents().name();
            if (!particle.equals(MissingTextureAtlasSprite.getLocation())) {
                return new Object[] { candidate, model };
            }
            if (fallback == null) {
                fallback = new Object[] { candidate, model };
            }
        }
        return fallback;
    }
}
