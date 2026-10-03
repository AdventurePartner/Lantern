package org.lantern.internal.mixin;

import java.util.UUID;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * P1 玩家宿主化：hostDriven 整替玩家跳过原版手持物层。
 *
 * PlayerItemInHandLayer 自身没有 submit（在父类 ItemInHandLayer，那里混入会波及
 * 所有生物），注入本类覆写的 submitArmWithItem——仅玩家路径生效。
 * 原版手持渲染在原版手臂挂点，与整替模型动画手臂位置不匹配；
 * 压制后手持由 CostumeItemLayer 渲染到 rightItem/leftItem 挂点骨。
 */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lantern$skipVanillaHeldItem(
        AvatarRenderState state,
        ItemStackRenderState itemState,
        HumanoidArm arm,
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        int packedLight,
        CallbackInfo ci
    ) {
        UUID playerId = state.getRenderData(CostumeRenderData.PLAYER_UUID);
        if (playerId != null && CostumeHandler.hasHostDrivenFullBody(playerId)) {
            ci.cancel();
        }
    }
}
