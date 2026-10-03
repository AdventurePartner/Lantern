package org.lantern.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.lantern.costume.attach.HostAttach;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.processing.AnimationProcessor;

/**
 * P1 附属层适配：鞘翅跟随躯干骨。
 *
 * 鞘翅走 ElytraModel（不是 HumanoidModel）且经 EquipmentLayerRenderer 的 WINGS
 * 层位提交，纹理与染色链与盔甲同源；这里只把 poseStack 换到躯干骨空间，
 * 鞘翅自身的展开动画（ElytraModel.setupAnim 读 renderState 的 elytraRot）不受影响。
 */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Unique
    private boolean lantern$pushed;

    @Inject(method = "submit", at = @At("HEAD"))
    private void lantern$enterBoneSpace(
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        int packedLight,
        HumanoidRenderState renderState,
        float bodyYaw,
        float pitch,
        CallbackInfo ci
    ) {
        this.lantern$pushed = false;
        if (!(renderState instanceof AvatarRenderState)) return;
        UUID playerId = renderState.getRenderData(CostumeRenderData.PLAYER_UUID);
        if (playerId == null || !CostumeHandler.hasHostDrivenFullBody(playerId)) return;
        AnimationProcessor<?> processor = HostAttach.bones(playerId);
        if (processor == null) return;

        poseStack.pushPose();
        if (HostAttach.applyBoneSpace(poseStack, processor, "body")) {
            this.lantern$pushed = true;
        } else {
            poseStack.popPose();
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void lantern$exitBoneSpace(
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        int packedLight,
        HumanoidRenderState renderState,
        float bodyYaw,
        float pitch,
        CallbackInfo ci
    ) {
        if (this.lantern$pushed) {
            this.lantern$pushed = false;
            poseStack.popPose();
        }
    }
}
