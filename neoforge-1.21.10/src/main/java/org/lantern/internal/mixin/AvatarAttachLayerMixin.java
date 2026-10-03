package org.lantern.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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
 * P1 附属层适配（第二类）：持有独立模型、直接用进入时 poseStack 的装饰层。
 *
 * 披风与肩上鹦鹉跟随躯干骨，deadmau5 耳朵跟随头骨。做法是把 poseStack 整体换到
 * 该骨驱动后的原版模型空间（HostAttach.applyBoneSpace），各层自身的模型与偏移
 * 一行不动——骨链恒等时该变换退化为恒等，非宿主化玩家与普通生物完全走原版路径。
 */
@Mixin({ CapeLayer.class, ParrotOnShoulderLayer.class, Deadmau5EarsLayer.class })
public abstract class AvatarAttachLayerMixin {

    @Unique
    private boolean lantern$pushed;

    @Inject(method = "submit", at = @At("HEAD"))
    private void lantern$enterBoneSpace(
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        int packedLight,
        AvatarRenderState renderState,
        float bodyYaw,
        float pitch,
        CallbackInfo ci
    ) {
        this.lantern$pushed = false;
        UUID playerId = renderState.getRenderData(CostumeRenderData.PLAYER_UUID);
        if (playerId == null || !CostumeHandler.hasHostDrivenFullBody(playerId)) return;
        AnimationProcessor<?> processor = HostAttach.bones(playerId);
        if (processor == null) return;

        // 耳朵长在头上，披风与鹦鹉挂在躯干
        String boneName = (Object)this instanceof Deadmau5EarsLayer ? "head" : "body";
        poseStack.pushPose();
        if (HostAttach.applyBoneSpace(poseStack, processor, boneName)) {
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
        AvatarRenderState renderState,
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
