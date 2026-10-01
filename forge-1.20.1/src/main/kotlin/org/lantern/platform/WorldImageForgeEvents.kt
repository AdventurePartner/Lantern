package org.lantern.platform

import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.lantern.worldimage.WorldImageRenderer

/**
 * 世界图片的 Forge 1.20.1 世界渲染钩子：AFTER_ENTITIES 阶段向共享 BufferSource 提交顶点。
 *
 * 1.20.1 是旧架构（与 1.21.x 相反，字节码实证）：renderLevel 直接接收**含相机旋转的
 * PoseStack**（GameRenderer 构建后传入），Forge 事件原样下发它，全局 modelview 矩阵
 * 在实体段/AFTER_ENTITIES 期间是 identity——所以这里必须用事件的 poseStack 与原版
 * 实体共用同一个基，传空栈会让图片失去视角旋转（按"永远朝北、俯仰 0"画）。
 * 顶点写入共享缓冲后由 renderLevel 末尾的无参 endBatch 兜底提交，这里不 flush
 */
object WorldImageForgeEvents {

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        WorldImageRenderer.render(
            event.poseStack,
            Minecraft.getInstance().renderBuffers().bufferSource(),
            event.camera,
            event.partialTick
        )
    }
}
