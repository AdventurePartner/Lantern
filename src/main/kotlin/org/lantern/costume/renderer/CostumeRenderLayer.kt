package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.costume.handler.CostumeHandler

class CostumeRenderLayer(
    parent: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(parent) {
    private val boneSnapshot = PlayerBoneSnapshot()

    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        player: AbstractClientPlayer,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTick: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        if (!CostumeHandler.hasAny(player.uuid)) {
            return
        }

        PlayerBoneSnapshot.capture(parentModel, boneSnapshot)
        poseStack.pushPose()
        try {
            poseStack.translate(0.0, 1.501, 0.0)
            poseStack.scale(-1.0f, -1.0f, 1.0f)
            CostumeHandler.renderForPlayer(player, poseStack, bufferSource, packedLight, partialTick, boneSnapshot)
        } finally {
            poseStack.popPose()
        }
    }
}
