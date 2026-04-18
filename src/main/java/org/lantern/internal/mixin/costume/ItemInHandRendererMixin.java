package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;
import org.lantern.Lantern;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderer.CostumeItemRenderer;
import org.lantern.model.block.LanternBlockEntity;
import org.lantern.model.block.LanternBlockRenderer;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Unique
    private static final float LANTERN$ITEM_IN_HAND_SCALE = 0.35f;

    @Unique
    private static LanternBlockRenderer lantern$blockRenderer;

    @Unique
    private static LanternBlockEntity lantern$cachedBlockEntity;
    @Unique
    private static BlockModelWrapper lantern$cachedBlockEntityWrapper;

    @Unique
    private static final Set<ResourceLocation> lantern$warnedModels = new HashSet<>();

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void lantern$renderItem(LivingEntity entity, ItemStack itemStack,
                                    ItemDisplayContext displayContext,
                                    boolean leftHand, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight,
                                    CallbackInfo ci) {
        // 1. Costume 系统：LanternModelId 标记的物品
        String modelId = lantern$getLanternModelId(itemStack);
        if (modelId != null) {
            CostumeItemRenderer renderer = CostumeHandler.INSTANCE.getItemRenderer(modelId);
            if (renderer != null) {
                renderer.render(poseStack, bufferSource, packedLight, 0f);
                ci.cancel();
                return;
            }
        }

        // 2. 方块物品 GeckoLib 3D 渲染：木桶 + custom_model_data → BlockRendererHandler
        if (!lantern$isCustomBlockItem(itemStack)) return;
        Integer customData = lantern$getCustomModelDataValue(itemStack);
        if (customData == null) {
            Lantern.INSTANCE.getLogger().warn("[Lantern] Barrel item has no CustomModelData");
            return;
        }
        BlockModelWrapper wrapper = BlockRendererHandler.INSTANCE.getWrapperByCustomModelData(customData);
        if (wrapper == null) {
            Lantern.INSTANCE.getLogger().warn("[Lantern] No wrapper for cmd={}, itemModelMap.size={}", customData,
                BlockRendererHandler.INSTANCE.getItemModelMapSize());
            return;
        }

        lantern$renderBlockModel(wrapper, poseStack, bufferSource, packedLight);
        ci.cancel();
    }

    @Unique
    private String lantern$getLanternModelId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        if (!customData.contains("LanternModelId")) return null;
        return customData.copyTag().getString("LanternModelId");
    }

    @Unique
    private Integer lantern$getCustomModelDataValue(ItemStack stack) {
        CustomModelData modern = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (modern != null) {
            return modern.value();
        }
        CustomData legacy = stack.get(DataComponents.CUSTOM_DATA);
        if (legacy == null) return null;
        var tag = legacy.copyTag();
        if (!tag.contains("CustomModelData", Tag.TAG_INT)) return null;
        return tag.getInt("CustomModelData");
    }

    @Unique
    private void lantern$renderBlockModel(BlockModelWrapper wrapper, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int packedLight) {
        if (lantern$blockRenderer == null) {
            lantern$blockRenderer = new LanternBlockRenderer();
        }
        if (lantern$cachedBlockEntity == null || lantern$cachedBlockEntityWrapper != wrapper) {
            lantern$cachedBlockEntity = new LanternBlockEntity(
                BlockPos.ZERO,
                Blocks.BARREL.defaultBlockState()
            );
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

            float scale = wrapper.getScale() * LANTERN$ITEM_IN_HAND_SCALE;
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5, -0.5, -0.5);
            lantern$blockRenderer.render(lantern$cachedBlockEntity, 0f, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException e) {
            // 限频：每个模型路径仅警告一次，避免每帧刷屏
            ResourceLocation modelLoc = wrapper.getModelLocation();
            if (lantern$warnedModels.add(modelLoc)) {
                Lantern.INSTANCE.getLogger().warn("Block model render failed for {}: {}", modelLoc, e.getMessage());
            }
            // 渲染 vanilla 黑紫缺失模型作为替代
            LanternBlockRenderer.renderMissingBlock(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            poseStack.popPose();
        }
    }

    @Unique
    private static boolean lantern$isCustomBlockItem(ItemStack stack) {
        return stack.is(Items.BARREL);
    }
}
