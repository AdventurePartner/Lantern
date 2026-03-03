package org.lantern.internal.mixin.item;

import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
            return;
        }

        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        BakedModel model = lantern$resolveModel(modelManager, identifier);
        if (model == null) {
            return;
        }

        cir.setReturnValue(model);
        cir.cancel();
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

    /**
     * 解析 identifier 對應的 BakedModel。
     *
     * ctx.addModels() 將模型注冊為 fabric_resource 變體（而非 inventory 變體），
     * 通過 FabricBakedModelManager.getModel(ResourceLocation) 可直接訪問此正確烘焙的模型。
     */
    @Unique
    private BakedModel lantern$resolveModel(ModelManager modelManager, String identifier) {
        String normalized = identifier.startsWith("item/") ? identifier.substring("item/".length()) : identifier;
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, normalized);

        // 優先通過 FabricBakedModelManager 獲取 fabric_resource 變體（由 ctx.addModels 正確烘焙）
        BakedModel fabricModel = ((FabricBakedModelManager) modelManager).getModel(resourceId);
        if (fabricModel != null && fabricModel != modelManager.getMissingModel()) {
            if (!fabricModel.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return fabricModel;
            }
        }

        // 降級：嘗試 inventory 變體
        ModelResourceLocation inventoryLoc = ModelResourceLocation.inventory(resourceId);
        BakedModel inventoryModel = modelManager.getModel(inventoryLoc);
        if (inventoryModel != null && inventoryModel != modelManager.getMissingModel()) {
            if (!inventoryModel.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return inventoryModel;
            }
        }

        return null;
    }
}
