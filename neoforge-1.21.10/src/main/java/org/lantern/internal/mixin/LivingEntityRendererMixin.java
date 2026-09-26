package org.lantern.internal.mixin;

import java.util.UUID;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * P1 玩家宿主化：hostDriven 整替外观在身时跳过原版玩家身体的渲染提交。
 *
 * v2（2026-09-04，压制方案升级）：getRenderType 返回 null —— LivingEntityRenderer.submit
 * 对 null rendertype 整个跳过 submitModel（零顶点、零深度、零片元），layers（含
 * CostumeRenderLayer 的 GeckoLib 整替模型）不受影响照常执行。
 * v1 的 getModelTint=0 方案被实测淘汰：隐形身体与整替模型几何高度重合（同为标准人形），
 * 渲染仍在发生并产生深度级干涉——症状为碎状随机透明面（z-fighting 型），仅玩家出现
 * （只有玩家存在双重渲染）。null 方案从提交层面消除干涉源。
 * 第一人称手臂在 ItemInHandRenderer，不受影响。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void lantern$skipVanillaBodyForHostDriven(
        LivingEntityRenderState state,
        boolean bodyVisible,
        boolean translucentBypass,
        boolean glowing,
        CallbackInfoReturnable<RenderType> cir
    ) {
        if (!(state instanceof AvatarRenderState)) return;
        UUID playerId = state.getRenderData(CostumeRenderData.PLAYER_UUID);
        if (playerId != null && CostumeHandler.hasHostDrivenFullBody(playerId)) {
            cir.setReturnValue(null);
        }
    }
}
