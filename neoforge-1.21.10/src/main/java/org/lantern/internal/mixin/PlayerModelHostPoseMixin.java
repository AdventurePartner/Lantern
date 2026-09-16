package org.lantern.internal.mixin;

import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.lantern.costume.attach.HostAttach;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.processing.AnimationProcessor;

/**
 * P1 附属层适配（第一类）：把原版 PlayerModel 的部件写成 Lantern 骨骼姿态。
 *
 * 靠 parentModel 定位的层——CustomHeadLayer 的头颅/南瓜、ArrowLayer 的插箭、
 * BeeStingerLayer 的蜂刺——读的都是这些部件的 translateAndRotate，写对了它们
 * 就自动跟随骨骼，各层定位代码一行不用改。
 *
 * 时机：LivingEntityRenderer.submit 在 layers 循环前立即调一次本模型的 setupAnim
 * （为各层提供部件姿态），本注入落在其尾部，是该链最后的写入者——PlayerModel.setupAnim
 * 先写 visible 再 super 到 HumanoidModel.setupAnim，后者单出口无 return。
 * 宿主模型自身已被 LivingEntityRendererMixin 压制不提交，改部件无视觉副作用。
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelHostPoseMixin {

    @Shadow @org.spongepowered.asm.mixin.Final public ModelPart head;
    @Shadow @org.spongepowered.asm.mixin.Final public ModelPart body;
    @Shadow @org.spongepowered.asm.mixin.Final public ModelPart rightArm;
    @Shadow @org.spongepowered.asm.mixin.Final public ModelPart leftArm;
    @Shadow @org.spongepowered.asm.mixin.Final public ModelPart rightLeg;
    @Shadow @org.spongepowered.asm.mixin.Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void lantern$writeHostPose(AvatarRenderState state, CallbackInfo ci) {
        UUID playerId = state.getRenderData(CostumeRenderData.PLAYER_UUID);
        if (playerId == null || !CostumeHandler.hasHostDrivenFullBody(playerId)) return;
        AnimationProcessor<?> processor = HostAttach.bones(playerId);
        if (processor == null) return;

        // 原版四肢是单段，Lantern 是两段——附属件挂大臂/大腿骨，肘膝以下的细节
        // 由整替模型自身承担，这里只需保证锚点跟随
        HostAttach.writePart(this.head, processor, "head");
        HostAttach.writePart(this.body, processor, "body");
        HostAttach.writePart(this.rightArm, processor, "rightArm");
        HostAttach.writePart(this.leftArm, processor, "leftArm");
        HostAttach.writePart(this.rightLeg, processor, "rightLeg");
        HostAttach.writePart(this.leftLeg, processor, "leftLeg");
    }
}
