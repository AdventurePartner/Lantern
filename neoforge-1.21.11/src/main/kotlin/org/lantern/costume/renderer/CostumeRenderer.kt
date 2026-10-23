package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.CameraRenderState
import org.lantern.animation.AnimationHost
import org.lantern.animation.applyPoseToBones
import org.lantern.costume.attach.HostSkeleton
import org.lantern.costume.bone.BoneRotation
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.geo.CostumeGeoModel
import org.lantern.costume.renderstate.CostumeRenderContext
import org.lantern.costume.renderstate.CostumeRenderData
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.neoforge.submitPass
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.BoneSnapshots
import software.bernie.geckolib.renderer.base.GeoRenderState
import software.bernie.geckolib.renderer.base.RenderPassInfo

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

    /** P1 手持物品渲染（正向骨链自管矩阵，见 CostumeItemLayer） */
    private val itemLayer = CostumeItemLayer()

    fun submit(
        poseStack: PoseStack,
        context: CostumeRenderContext,
        submitNodes: SubmitNodeCollector,
        cameraState: CameraRenderState,
        packedLight: Int,
        partialTick: Float
    ) = submitPass(animatable, context, poseStack, submitNodes, cameraState, packedLight, partialTick)

    override fun getInstanceId(animatable: CostumeAnimatable, relatedObject: CostumeRenderContext?): Long =
        relatedObject?.playerId?.let { it.mostSignificantBits xor it.leastSignificantBits } ?: 0L

    override fun addRenderData(
        animatable: CostumeAnimatable,
        relatedObject: CostumeRenderContext?,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        val context = relatedObject ?: return
        if (wrapper.hostDriven) {
            // P1 玩家宿主化：AnimationHost 驱动（与替换实体同一播放器内核），姿势随渲染状态
            // 带到 adjustModelBonesForRender 再挂上骨骼；hostDriven 下 GeckoLib 控制器不参与
            val entity = context.entity ?: return
            val pose = AnimationHost.drivePose(entity, wrapper.animationLocation, wrapper.animationStates)
            if (pose != null) renderState.addGeckolibData(CostumeRenderData.HOST_POSE, pose)
            renderState.addGeckolibData(CostumeRenderData.HOST_CONTEXT, context)
            return
        }
        renderState.addGeckolibData(CostumeRenderData.PLAYER_POSE, context.pose)
        renderState.addGeckolibData(CostumeRenderData.ANIMATION_STATE, context.animationState)
    }

    override fun applyAnimationControllers(renderPassInfo: RenderPassInfo<GeoRenderState>, boneSnapshots: BoneSnapshots) {
        if (wrapper.hostDriven) return
        super.applyAnimationControllers(renderPassInfo, boneSnapshots)
    }

    override fun adjustModelBonesForRender(renderPassInfo: RenderPassInfo<GeoRenderState>, snapshots: BoneSnapshots) {
        if (wrapper.hostDriven) {
            applyPoseToBones(renderPassInfo.model(), snapshots, renderPassInfo.getGeckolibData(CostumeRenderData.HOST_POSE))
            return
        }
        if (!wrapper.boneSyncEnabled) return
        // 跟随原版玩家部件：在控制器结果之上整体覆写这几根骨的旋转（快照是相对 baseRot 的增量）
        val snapshot = renderPassInfo.getGeckolibData(CostumeRenderData.PLAYER_POSE) ?: return
        val mapping = wrapper.boneMapping
        applyRotation(renderPassInfo, snapshots, mapping.head, snapshot.head)
        applyRotation(renderPassInfo, snapshots, mapping.body, snapshot.body)
        applyRotation(renderPassInfo, snapshots, mapping.leftArm, snapshot.leftArm)
        applyRotation(renderPassInfo, snapshots, mapping.rightArm, snapshot.rightArm)
        applyRotation(renderPassInfo, snapshots, mapping.leftLeg, snapshot.leftLeg)
        applyRotation(renderPassInfo, snapshots, mapping.rightLeg, snapshot.rightLeg)
    }

    private fun applyRotation(
        renderPassInfo: RenderPassInfo<GeoRenderState>,
        snapshots: BoneSnapshots,
        name: String,
        rotation: BoneRotation
    ) {
        val bone = renderPassInfo.model().getBone(name).orElse(null) ?: return
        snapshots.ifPresent(name) {
            it.setRotation(rotation.x - bone.baseRotX(), rotation.y - bone.baseRotY(), rotation.z - bone.baseRotZ())
        }
    }

    override fun adjustRenderPose(renderPassInfo: RenderPassInfo<GeoRenderState>) {
        // 外层变换推导（2026-09-04 重分析定稿）：原版管线到 RenderLayer 的栈为
        // R·S(-1,-1,1)·T(0,-1.501,0)，CostumeRenderLayer 叠加 T(0,1.501,0)·S(-1,-1,1)
        // ——两段互为精确逆变换，净效果 = 仅实体朝向 R。即本渲染器处于
        // 实体本地空间（y 向上、原点脚底），与 GeoReplacedEntityRenderer 的模型空间一致，
        // 标准 bedrock geo 直接正立渲染，不做任何补偿
        renderPassInfo.poseStack().translate(wrapper.offsetX, wrapper.offsetY, wrapper.offsetZ)
    }

    /**
     * 渲染图层阶段 poseStack 仍处于模型根空间（缩放与 adjustRenderPose 之后、出栈之前），
     * 手持物品在这里按正向骨链挂到挂点骨上
     */
    override fun applyRenderLayers(renderPassInfo: RenderPassInfo<GeoRenderState>, renderTasks: SubmitNodeCollector) {
        super.applyRenderLayers(renderPassInfo, renderTasks)
        if (!wrapper.hostDriven) return
        val context = renderPassInfo.getGeckolibData(CostumeRenderData.HOST_CONTEXT) ?: return
        val skeleton = HostSkeleton(renderPassInfo.model(), renderPassInfo.getGeckolibData(CostumeRenderData.HOST_POSE))
        itemLayer.submitItems(renderPassInfo.poseStack(), renderTasks, context, skeleton, renderPassInfo.packedLight())
    }
}
