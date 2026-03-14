package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderer.CostumeItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void lantern$renderItem(LivingEntity entity, ItemStack itemStack,
                                    ItemDisplayContext displayContext,
                                    boolean leftHand, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight,
                                    CallbackInfo ci) {
        String modelId = lantern$getLanternModelId(itemStack);
        if (modelId == null) return;

        CostumeItemRenderer renderer = CostumeHandler.INSTANCE.getItemRenderer(modelId);
        if (renderer == null) return;

        renderer.render(poseStack, bufferSource, packedLight, 0f);
        ci.cancel();
    }

    @Unique
    private String lantern$getLanternModelId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        if (!customData.contains("LanternModelId")) return null;
        return customData.copyTag().getString("LanternModelId");
    }
}
