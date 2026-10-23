package org.lantern.animation

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import java.util.concurrent.ConcurrentHashMap
import software.bernie.geckolib.cache.model.BakedGeoModel
import software.bernie.geckolib.cache.model.GeoBone
import software.bernie.geckolib.renderer.base.BoneSnapshots

private val boneNameLowerCache = ConcurrentHashMap<String, String>()

/** 骨骼名小写缓存：动画轨道以小写为键，骨骼名数量有限，避免每帧逐骨分配字符串 */
private fun lowerBoneName(name: String): String =
    boneNameLowerCache.getOrPut(name) { name.lowercase() }

/**
 * GeckoLib 5.4 起骨骼不可变，本帧姿势只能经 BoneSnapshot 在延迟绘制期间挂上去，且快照是增量：
 * 旋转叠加在骨骼自带的 baseRot 上，平移是偏移，缩放是乘数。AnimationPlayer 输出的是
 * 5.3 语义的绝对值（旋转直接替换骨骼旋转，缺轴记 NaN 回落静态值），这里换算成增量，
 * 画面与 5.3 时一致。剪辑没驱动的骨骼不建快照，即静态姿势。
 *
 * 在 GeoRenderer.adjustModelBonesForRender（BoneUpdater）里调用，渲染线程串行
 */
fun applyPoseToBones(model: BakedGeoModel, snapshots: BoneSnapshots, pose: Map<String, FloatArray>?) {
    if (pose.isNullOrEmpty()) return
    for (bone in model.boneLookup().get().values) {
        val v = pose[lowerBoneName(bone.name())] ?: continue
        val snapshot = snapshots.get(bone.name()).orElse(null) ?: continue
        snapshot.setRotation(
            if (v[0].isNaN()) 0f else v[0] - bone.baseRotX(),
            if (v[1].isNaN()) 0f else v[1] - bone.baseRotY(),
            if (v[2].isNaN()) 0f else v[2] - bone.baseRotZ()
        )
        snapshot.setTranslation(
            if (v[3].isNaN()) 0f else v[3],
            if (v[4].isNaN()) 0f else v[4],
            if (v[5].isNaN()) 0f else v[5]
        )
        snapshot.setScale(
            if (v[6].isNaN()) 1f else v[6],
            if (v[7].isNaN()) 1f else v[7],
            if (v[8].isNaN()) 1f else v[8]
        )
    }
}

/**
 * 渲染过程之外读骨骼姿态（盔甲、披风/鞘翅、手持物、原版部件跟骨）：5.4 在绘制之外不保留姿态，
 * 这里按同一份姿势表算出 5.3 语义的骨骼字段，矩阵运算与 5.3 RenderUtil.prepMatrixForBone 同序
 */
object PosedBones {

    /** 本帧姿态下某骨的 rot(弧度)/pos(像素)/scale 九值，与 5.3 GeoBone 的 rotX…scaleZ 同义 */
    @JvmStatic
    fun values(bone: GeoBone, pose: Map<String, FloatArray>?): FloatArray {
        val v = pose?.get(lowerBoneName(bone.name()))
        return floatArrayOf(
            if (v == null || v[0].isNaN()) bone.baseRotX() else v[0],
            if (v == null || v[1].isNaN()) bone.baseRotY() else v[1],
            if (v == null || v[2].isNaN()) bone.baseRotZ() else v[2],
            if (v == null || v[3].isNaN()) 0f else v[3],
            if (v == null || v[4].isNaN()) 0f else v[4],
            if (v == null || v[5].isNaN()) 0f else v[5],
            if (v == null || v[6].isNaN()) 1f else v[6],
            if (v == null || v[7].isNaN()) 1f else v[7],
            if (v == null || v[8].isNaN()) 1f else v[8]
        )
    }

    @JvmStatic
    fun prepMatrixForBone(poseStack: PoseStack, bone: GeoBone, pose: Map<String, FloatArray>?) {
        val v = values(bone, pose)
        translateToBone(poseStack, v)
        translateToPivot(poseStack, bone)
        rotate(poseStack, v)
        scale(poseStack, v)
        translateAwayFromPivot(poseStack, bone)
    }

    @JvmStatic
    fun translateToBone(poseStack: PoseStack, v: FloatArray) {
        poseStack.translate(-v[3] / 16f, v[4] / 16f, v[5] / 16f)
    }

    @JvmStatic
    fun rotate(poseStack: PoseStack, v: FloatArray) {
        if (v[2] != 0f) poseStack.mulPose(Axis.ZP.rotation(v[2]))
        if (v[1] != 0f) poseStack.mulPose(Axis.YP.rotation(v[1]))
        if (v[0] != 0f) poseStack.mulPose(Axis.XP.rotation(v[0]))
    }

    @JvmStatic
    fun scale(poseStack: PoseStack, v: FloatArray) {
        poseStack.scale(v[6], v[7], v[8])
    }

    private fun translateToPivot(poseStack: PoseStack, bone: GeoBone) {
        poseStack.translate(bone.pivotX() / 16f, bone.pivotY() / 16f, bone.pivotZ() / 16f)
    }

    private fun translateAwayFromPivot(poseStack: PoseStack, bone: GeoBone) {
        poseStack.translate(-bone.pivotX() / 16f, -bone.pivotY() / 16f, -bone.pivotZ() / 16f)
    }
}
