package org.lantern.platform

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.lantern.worldimage.WorldImageRenderer

/**
 * 世界图片的 Forge 世界渲染钩子：AFTER_ENTITIES 阶段向共享 BufferSource 提交顶点。
 *
 * 1.21.1 的架构是"相机旋转在全局 RenderSystem 矩阵、实体阶段 posestack 为 identity"
 * （字节码实证：dispatch 把同一个 frustumMatrix 同时当 poseStack/projectionMatrix 下发，
 * 而 GameRenderer/LevelRenderer 已 getModelViewStack+applyModelViewMatrix）——
 * 所以这里必须用空 PoseStack，把事件矩阵再乘进去会让视图旋转生效两次、图片随视角漂移。
 * 事件自带的 partialTick 是帧间隔（getRealtimeDeltaTicks）而非插值 tick，插值从
 * Minecraft 的 DeltaTracker 取。顶点写入共享缓冲后由 renderLevel 末尾的
 * 无参 endBatch 兜底提交，这里不 flush（提前全刷会打乱原版批次顺序）
 */
object WorldImageForgeEvents {

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        val client = Minecraft.getInstance()
        WorldImageRenderer.render(
            PoseStack(),
            client.renderBuffers().bufferSource(),
            event.camera,
            client.timer.getGameTimeDeltaPartialTick(false)
        )
    }
}
