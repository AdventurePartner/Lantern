package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.state.CameraRenderState
import org.lantern.animation.AnimationHost
import org.lantern.animation.applyPoseToBones
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.geo.CostumeGeoModel
import org.lantern.costume.renderstate.CostumeRenderContext
import org.lantern.costume.renderstate.CostumeRenderData
import org.lantern.costume.wrapper.CostumeModelWrapper
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.GeoRenderState

class CostumeRenderer(
    private val wrapper: CostumeModelWrapper,
    val animatable: CostumeAnimatable
) : GeoObjectRenderer<CostumeAnimatable, CostumeRenderContext, GeoRenderState>(CostumeGeoModel(wrapper)) {

    val geoModel: CostumeGeoModel = super.getGeoModel() as CostumeGeoModel

    /** P1 玩家宿主化：绑定归属玩家（皮肤贴图来源），由 CostumeHandler 创建渲染器时调用 */
    fun bindPlayer(playerId: java.util.UUID) {
        geoModel.bindPlayer(playerId)
    }

    init {
        scaleWidth = wrapper.scale
        scaleHeight = wrapper.scale
    }

    /** P1 手持物品渲染（postRender 阶段调用，正向骨链自管矩阵，见 CostumeItemLayer） */
    private val itemLayer by lazy { CostumeItemLayer(this) }

    override fun postRender(
        renderState: GeoRenderState,
        poseStack: PoseStack,
        model: BakedGeoModel,
        renderTasks: net.minecraft.client.renderer.SubmitNodeCollector,
        cameraState: CameraRenderState,
        packedLight: Int,
        packedOverlay: Int,
        renderColor: Int
    ) {
        // postRender 的 poseStack 处于模型根空间（adjustRenderPose 之后）；
        // 从 ticket 取回本帧 context（addRenderData 存入），正向骨链渲染手持
        if (wrapper.hostDriven) {
            renderState.getGeckolibData(CostumeRenderData.HOST_CONTEXT)?.let { ctx ->
                itemLayer.submitItems(poseStack, renderTasks, ctx, packedLight)
            }
        }
        super.postRender(renderState, poseStack, model, renderTasks, cameraState, packedLight, packedOverlay, renderColor)
    }

    override fun getInstanceId(animatable: CostumeAnimatable, relatedObject: CostumeRenderContext): Long =
        relatedObject.playerId.mostSignificantBits xor relatedObject.playerId.leastSignificantBits

    override fun addRenderData(
        animatable: CostumeAnimatable,
        relatedObject: CostumeRenderContext,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        if (wrapper.hostDriven) {
            // P1 玩家宿主化：AnimationHost 驱动（与替换实体同一播放器内核）。
            // GeoObjectRenderer.submit 内部 fillRenderState -> 本回调 -> submitRenderTasks
            // （画骨），回调点处于渲染串行段，写骨安全；hostDriven 下 GeckoLib 动画
            // 管线已被 CostumeGeoModel 置空，本写入是骨骼唯一写入方
            val entity = relatedObject.entity ?: return
            val pose = AnimationHost.drivePose(entity, wrapper.animationLocation, wrapper.animationStates)
            renderState.addGeckolibData(CostumeRenderData.HOST_POSE, pose)
            renderState.addGeckolibData(CostumeRenderData.HOST_CONTEXT, relatedObject)
            applyPoseToBones(geoModel.animationProcessor, pose, geoModel.initialPose)
            if (diagTicks-- > 0 && diagTicks % 20 == 0) {
                val p = geoModel.animationProcessor
                fun bs(name: String): String {
                    val b = p.getBone(name) ?: return "$name=null"
                    return "%s(rot=%.2f,%.2f,%.2f pos=%.1f,%.1f,%.1f)".format(
                        name, b.rotX, b.rotY, b.rotZ, b.posX, b.posY, b.posZ
                    )
                }
                org.lantern.Lantern.logger.info(
                    "[Lantern][P1Diag] states[{}] | {} | BONES {} {} {}",
                    wrapper.animationStates.table.size,
                    AnimationHost.describe(relatedObject.playerId),
                    bs("body"), bs("head"), bs("rightLeg")
                )
            }
            return
        }
        renderState.addGeckolibData(CostumeRenderData.PLAYER_POSE, relatedObject.pose)
        renderState.addGeckolibData(CostumeRenderData.ANIMATION_STATE, relatedObject.animationState)
    }

    /** 诊断窗口：每 20 帧打一次（前 3600 帧=约 60 秒，覆盖完整操作序列排查） */
    private var diagTicks = 3600

    override fun adjustRenderPose(
        renderState: GeoRenderState,
        poseStack: PoseStack,
        model: BakedGeoModel,
        cameraState: CameraRenderState
    ) {
        // 外层变换推导（2026-09-04 重分析定稿）：原版管线到 RenderLayer 的栈为
        // R·S(-1,-1,1)·T(0,-1.501,0)，CostumeRenderLayer 叠加 T(0,1.501,0)·S(-1,-1,1)
        // ——两段互为精确逆变换，净效果 = 仅实体朝向 R。即本渲染器处于
        // 实体本地空间（y 向上、原点脚底），与 GeoReplacedEntityRenderer 的模型空间一致，
        // 标准 bedrock geo 直接正立渲染，不做任何补偿
        poseStack.translate(wrapper.offsetX, wrapper.offsetY, wrapper.offsetZ)
    }
}
