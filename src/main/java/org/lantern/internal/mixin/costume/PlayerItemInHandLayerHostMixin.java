package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lantern.costume.attach.HostAttach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** hostDriven 整替外观：手持物改由 CostumeItemLayer 挂到 rightItem / leftItem 骨上，原版那份不画 */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerHostMixin {

    @Inject(
        method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void lantern$skipHostHands(
        LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, HumanoidArm arm,
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci
    ) {
        if (HostAttach.bones(entity) != null) {
            ci.cancel();
        }
    }
}
