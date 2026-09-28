package org.lantern.platform

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.lantern.worldimage.WorldImageRenderer

/**
 * 世界图片的 Forge 世界渲染钩子：AFTER_ENTITIES 阶段提交顶点并立即 flush。
 * 用渲染缓冲池自带的 BufferSource，与实体共用深度（会被地形/实体正确遮挡）。
 * 1.21.1 的事件把"poseStack"以 Matrix4f 下发（实体阶段的基矩阵），
 * 需要自己包回 PoseStack
 */
object WorldImageForgeEvents {

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        val poseStack = PoseStack()
        poseStack.last().pose().mul(event.poseStack)
        val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
        WorldImageRenderer.render(poseStack, buffers, event.camera, event.partialTick)
        buffers.endBatch()
    }
}
