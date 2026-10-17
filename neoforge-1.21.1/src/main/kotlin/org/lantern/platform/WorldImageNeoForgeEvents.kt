package org.lantern.platform

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.lantern.worldimage.WorldImageRenderer

/**
 * 世界图片的 NeoForge 1.21.1 世界渲染钩子：AFTER_ENTITIES 阶段向共享 BufferSource 提交顶点。
 *
 * 1.21.1 的相机旋转在全局 RenderSystem 模型视图矩阵里，实体阶段的 posestack 是 identity，
 * 必须传空 PoseStack；把事件矩阵再乘进去会让视图旋转生效两次、图片随视角漂移。
 * 顶点写入共享缓冲后由 renderLevel 末尾的无参 endBatch 兜底提交，这里不 flush
 * （提前全刷会打乱原版批次顺序）
 */
object WorldImageNeoForgeEvents {

    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        val client = Minecraft.getInstance()
        WorldImageRenderer.render(
            PoseStack(),
            client.renderBuffers().bufferSource(),
            event.camera,
            event.partialTick.getGameTimeDeltaPartialTick(false)
        )
    }
}
