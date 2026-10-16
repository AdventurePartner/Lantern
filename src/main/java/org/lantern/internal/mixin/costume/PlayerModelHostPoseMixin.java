package org.lantern.internal.mixin.costume;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.lantern.costume.attach.HostAttach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hostDriven 整替外观：把原版 PlayerModel 的六部件写成 Lantern 骨骼姿态，
 * 头颅、插箭、蜂刺、盔甲这些按 parentModel 部件定位的层就自动跟随骨骼。
 * 只在 LivingEntityRendererHostMixin 标记的那次渲染里生效，第一人称手臂的 setupAnim 不受影响。
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelHostPoseMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void lantern$writeHostPose(
        LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        HostAttach.writeHostPose((HumanoidModel<?>) (Object) this, entity);
    }
}
