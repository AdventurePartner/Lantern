package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.renderstate.CostumeRenderContext
import org.lantern.costume.renderstate.CostumeRenderData

class CostumeRenderLayer(
    parent: RenderLayerParent<AvatarRenderState, PlayerModel>
) : RenderLayer<AvatarRenderState, PlayerModel>(parent) {
    override fun submit(
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        packedLight: Int,
        renderState: AvatarRenderState,
        bodyYaw: Float,
        pitch: Float
    ) {
        // 隐身对齐 vanilla 身体语义：layers 循环不被隐身门控（AvatarRenderer 只挡
        // spectator），判定由各 layer 自行负责（CapeLayer 同款显式判 isInvisible）。
        // 盔甲/手持层有意不判隐身是原版行为，整替模型是身体替身，跟随身体消失；
        // 队友可见隐身的 15% 半透明（translucentBypass）不复制，直接不画
        if (renderState.isInvisible) return
        val playerId = requireNotNull(renderState.getRenderData(CostumeRenderData.PLAYER_UUID))
        if (!CostumeHandler.hasAny(playerId)) return
        val cameraState = requireNotNull(renderState.getRenderData(CostumeRenderData.CAMERA_STATE))
        val context = CostumeRenderContext.from(
            playerId,
            PlayerBoneSnapshot.capture(parentModel),
            renderState
        )

        poseStack.pushPose()
        try {
            poseStack.translate(0.0, 1.501, 0.0)
            poseStack.scale(-1.0f, -1.0f, 1.0f)
            CostumeHandler.submitForPlayer(
                context,
                poseStack,
                submitNodes,
                cameraState,
                packedLight,
                renderState.partialTick
            )
        } finally {
            poseStack.popPose()
        }
    }
}
