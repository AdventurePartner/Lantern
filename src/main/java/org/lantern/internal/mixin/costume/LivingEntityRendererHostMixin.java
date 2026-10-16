package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.lantern.costume.attach.HostAttach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * hostDriven 整替外观：玩家渲染的入口与出口，以及原版身体压制。
 *
 * 姿势必须在 setupAnim 之前算好——PlayerModel 部件与披风等附属层都在 CostumeRenderLayer
 * 之前读骨骼。getRenderType 返回 null 时原版跳过身体提交（隐身玩家走的就是这条路），
 * 各 layer 照常执行；只压透明度会和整替模型产生深度干涉，1.21.10 实测过。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererHostMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void lantern$beginHost(
        LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferSource, int packedLight, CallbackInfo ci
    ) {
        HostAttach.begin(entity);
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN")
    )
    private void lantern$endHost(
        LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferSource, int packedLight, CallbackInfo ci
    ) {
        HostAttach.end(entity);
    }

    @Inject(
        method = "getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void lantern$hideHostBody(
        LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing,
        CallbackInfoReturnable<RenderType> cir
    ) {
        if (HostAttach.bones(entity) != null) {
            cir.setReturnValue(null);
        }
    }
}
