package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.lantern.costume.attach.HostAttach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** hostDriven 整替外观：鞘翅跟随躯干骨（ElytraModel 部件自成一套，不读 parentModel，不会转两遍） */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerHostMixin {

    @Unique
    private boolean lantern$pushed;

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD")
    )
    private void lantern$enterBoneSpace(
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, LivingEntity entity,
        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        this.lantern$pushed = HostAttach.enterBoneSpace(poseStack, entity, "body");
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("RETURN")
    )
    private void lantern$exitBoneSpace(
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, LivingEntity entity,
        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        if (this.lantern$pushed) {
            this.lantern$pushed = false;
            poseStack.popPose();
        }
    }
}
