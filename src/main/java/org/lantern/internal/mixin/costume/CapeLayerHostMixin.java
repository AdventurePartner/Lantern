package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.lantern.costume.attach.HostAttach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hostDriven 整替外观：披风跟随躯干骨。
 * deadmau5 耳朵不在这里处理——1.21.1 / 1.20.1 的 renderEars 会 copyFrom(head)，
 * 头部件已由 PlayerModelHostPoseMixin 写成骨骼姿态，再换骨骼空间会转两遍。
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerHostMixin {

    @Unique
    private boolean lantern$pushed;

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At("HEAD")
    )
    private void lantern$enterBoneSpace(
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        this.lantern$pushed = HostAttach.enterBoneSpace(poseStack, player, "body");
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At("RETURN")
    )
    private void lantern$exitBoneSpace(
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        if (this.lantern$pushed) {
            this.lantern$pushed = false;
            poseStack.popPose();
        }
    }
}
