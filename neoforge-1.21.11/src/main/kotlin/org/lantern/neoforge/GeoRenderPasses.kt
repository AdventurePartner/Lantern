package org.lantern.neoforge

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.CameraRenderState
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.constant.DataTickets
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.GeoRenderState

/**
 * 提交一次 GeoObjectRenderer 渲染。GeckoLib 5.4 的 GeoObjectRenderer.performRenderPass 把 partialTick
 * 声明成 int，传进去会把帧内插值截成 0（控制器动画逐 tick 跳帧）；这里照它的步骤自己填渲染状态
 */
fun <T : GeoAnimatable, O, R : GeoRenderState> GeoObjectRenderer<T, O, R>.submitPass(
    animatable: T,
    relatedObject: O,
    poseStack: PoseStack,
    renderTasks: SubmitNodeCollector,
    cameraState: CameraRenderState,
    packedLight: Int,
    partialTick: Float
) {
    val renderState = fillRenderState(animatable, relatedObject, createRenderState(animatable, relatedObject), partialTick)
    renderState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight)
    performRenderPass(renderState, poseStack, renderTasks, cameraState)
}
