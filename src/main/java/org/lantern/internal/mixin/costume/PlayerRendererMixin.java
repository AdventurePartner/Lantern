package org.lantern.internal.mixin.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import org.lantern.costume.bone.PlayerBoneSnapshot;
import org.lantern.costume.handler.CostumeHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Unique
    private static final PlayerBoneSnapshot lantern$snapshot = new PlayerBoneSnapshot();

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void lantern$renderCostume(AbstractClientPlayer player, float entityYaw, float partialTick,
                                       PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> playerModel =
                (PlayerModel<AbstractClientPlayer>) ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        PlayerBoneSnapshot.capture(playerModel, lantern$snapshot);
        CostumeHandler.INSTANCE.renderForPlayer(player, poseStack, bufferSource, packedLight, partialTick, lantern$snapshot);
    }
}
