package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.world.entity.player.Player;
import org.lantern.costume.attach.HostAttach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** hostDriven 整替外观：肩上鹦鹉跟随躯干骨 */
@Mixin(ParrotOnShoulderLayer.class)
public abstract class ParrotOnShoulderLayerHostMixin {

    @Unique
    private boolean lantern$pushed;

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/player/Player;FFFFFF)V",
        at = @At("HEAD")
    )
    private void lantern$enterBoneSpace(
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Player player,
        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        this.lantern$pushed = HostAttach.enterBoneSpace(poseStack, player, "body");
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/player/Player;FFFFFF)V",
        at = @At("RETURN")
    )
    private void lantern$exitBoneSpace(
        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Player player,
        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        if (this.lantern$pushed) {
            this.lantern$pushed = false;
            poseStack.popPose();
        }
    }
}
