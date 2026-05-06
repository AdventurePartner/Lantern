package org.lantern.internal.mixin.item;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.lantern.Lantern;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.model.block.LanternBlockEntity;
import org.lantern.model.block.LanternBlockRenderer;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.lantern.platform.IdentifierBridge;
import org.lantern.platform.ModelLocationBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public abstract class ForgeItemRendererMixin {

    @Unique
    private static LanternBlockRenderer lantern$blockRenderer;

    @Unique
    private static LanternBlockEntity lantern$cachedBlockEntity;
    @Unique
    private static BlockModelWrapper lantern$cachedBlockEntityWrapper;

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void lantern$getModel(ItemStack itemStack,
                                  Level level,
                                  LivingEntity entity,
                                  int seed,
                                  CallbackInfoReturnable<BakedModel> cir) {
        Integer customData = lantern$getCustomModelDataValue(itemStack);
        if (customData == null) return;

        String identifier = ResourceHandler.INSTANCE.getItemIcon(customData);
        if (identifier == null) return;

        BakedModel model = lantern$resolveModel(Minecraft.getInstance().getModelManager(), identifier);
        if (model == null) return;

        cir.setReturnValue(model);
        cir.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lantern$renderBlockItem(ItemStack stack, ItemDisplayContext displayContext,
                                         boolean leftHand, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, int overlay,
                                         BakedModel model, CallbackInfo ci) {
        if (!stack.is(Items.BARREL)) return;
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
            float offsetX = wrapper.getItemOffsetX();
            float offsetY = wrapper.getItemOffsetY();
            float offsetZ = wrapper.getItemOffsetZ();
            if (offsetX != 0f || offsetY != 0f || offsetZ != 0f) {
                poseStack.translate(offsetX, offsetY, offsetZ);
            }

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

    @Unique
    private BakedModel lantern$resolveModel(ModelManager modelManager, String identifier) {
        String normalized = identifier.startsWith("item/") ? identifier.substring("item/".length()) : identifier;
        ResourceLocation resourceId = IdentifierBridge.of(Lantern.MOD_ID, normalized);
        ModelResourceLocation inventoryLoc = ModelLocationBridge.inventory(resourceId);
        BakedModel inventoryModel = modelManager.getModel(inventoryLoc);
        if (inventoryModel != null && inventoryModel != modelManager.getMissingModel()) {
            if (!inventoryModel.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return inventoryModel;
            }
        }
        return null;
    }
}
