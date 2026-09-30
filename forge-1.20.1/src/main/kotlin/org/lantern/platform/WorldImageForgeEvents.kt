package org.lantern.platform

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.lantern.worldimage.WorldImageRenderer

/**
 * 世界图片的 Forge 1.20.1 世界渲染钩子：AFTER_ENTITIES 阶段向共享 BufferSource 提交顶点。
 *
 * 与 1.21.1 同架构（相机旋转在全局 RenderSystem 矩阵、实体阶段 posestack 为 identity），
 * 用空 PoseStack，不消费事件下发的姿态矩阵。顶点写入共享缓冲后由 renderLevel 末尾的
 * 无参 endBatch 兜底提交，这里不 flush（提前全刷会打乱原版批次顺序）
 */
object WorldImageForgeEvents {

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        WorldImageRenderer.render(
            PoseStack(),
            Minecraft.getInstance().renderBuffers().bufferSource(),
            event.camera,
            event.partialTick
        )
    }
}
