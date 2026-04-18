package org.lantern.internal.mixin.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import org.lantern.Lantern;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.model.block.LanternBlockEntity;
import org.lantern.model.block.LanternBlockRenderer;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Unique
    private static LanternBlockRenderer lantern$blockRenderer;

    @Unique
    private static LanternBlockEntity lantern$cachedBlockEntity;
    @Unique
    private static BlockModelWrapper lantern$cachedBlockEntityWrapper;


    // ==================== Model Resolution (existing) ====================

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

    // ==================== Block Item GeckoLib Rendering ====================
    // ItemInHandRendererMixin handles first/third person hand rendering.
    // This injection handles ALL other contexts: GUI, ground, item frame, etc.

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lantern$renderBlockItem(ItemStack stack, ItemDisplayContext displayContext,
                                         boolean leftHand, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, int overlay,
                                         BakedModel model, CallbackInfo ci) {
        if (!lantern$isCustomBlockItem(stack)) return;
        Integer cmd = lantern$getCustomModelDataValue(stack);
        if (cmd == null) return;
        BlockModelWrapper wrapper = BlockRendererHandler.INSTANCE.getWrapperByCustomModelData(cmd);
        if (wrapper == null) return;

        if (lantern$blockRenderer == null) {
            lantern$blockRenderer = new LanternBlockRenderer();
        }

        if (lantern$cachedBlockEntity == null || lantern$cachedBlockEntityWrapper != wrapper) {
            lantern$cachedBlockEntity = new LanternBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
            lantern$cachedBlockEntity.setWrapper(wrapper);
            lantern$cachedBlockEntityWrapper = wrapper;
        }

        poseStack.pushPose();
        try {
            // 应用物品偏移
            float offsetX = wrapper.getItemOffsetX();
            float offsetY = wrapper.getItemOffsetY();
            float offsetZ = wrapper.getItemOffsetZ();
            if (offsetX != 0f || offsetY != 0f || offsetZ != 0f) {
                poseStack.translate(offsetX, offsetY, offsetZ);
            }

            // GUI 场景下添加等距旋转，与原版方块 GUI 渲染一致
            if (displayContext == ItemDisplayContext.GUI) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(30f));
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(225f));
            }

            float scale = wrapper.getScale() * 0.625f;
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5, -0.5, -0.5);
            lantern$blockRenderer.render(lantern$cachedBlockEntity, 0f, poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException e) {
            LanternBlockRenderer.renderMissingBlock(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        } finally {
            poseStack.popPose();
        }
        ci.cancel();
    }
    @Unique
    private static boolean lantern$isCustomBlockItem(ItemStack stack) {
        return stack.is(Items.BARREL);
    }

    // ==================== Utilities ====================

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
        var tag = legacy.copyTag();
        if (!tag.contains("CustomModelData", Tag.TAG_INT)) {
            return null;
        }
        return tag.getInt("CustomModelData");
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
