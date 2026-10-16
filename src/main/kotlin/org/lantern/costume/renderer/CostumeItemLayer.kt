package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ShieldItem
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.util.RenderUtil

/**
 * hostDriven 外观的手持物：挂到整替模型的 rightItem / leftItem 骨，盾挂 rightShield / leftShield。
 * Fabric 1.21.1 / Forge 1.21.1 / Forge 1.20.1 共用（1.21.10 走 ItemStackRenderState，有自己的一份）。
 *
 * 变换链：
 * 1. 正向骨链到挂点骨的父级，补上前臂——geo 里 rightItem 挂在上臂下、与前臂同级，
 *    不补的话肘部一弯物品就留在上臂延长线上
 * 2. 挂点骨自身的旋转与缩放绕握点施加（见 applyAttachBone）
 * 3. rotZ(180°) 把渲染器的恒等空间换回原版 ModelPart 空间
 * 4. 原版第三人称持握变换（ItemInHandLayer.renderArmWithItem 同序）后交给 ItemInHandRenderer
 */
class CostumeItemLayer(
    private val renderer: CostumeRenderer
) {

    fun renderItems(poseStack: PoseStack, entity: LivingEntity, bufferSource: MultiBufferSource, packedLight: Int) {
        val mainHand = entity.mainArm == HumanoidArm.RIGHT
        renderHand(
            poseStack, bufferSource, entity,
            if (mainHand) "rightItem" else "leftItem",
            entity.mainHandItem,
            if (mainHand) ItemDisplayContext.THIRD_PERSON_RIGHT_HAND else ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
            packedLight
        )
        renderHand(
            poseStack, bufferSource, entity,
            if (mainHand) "leftItem" else "rightItem",
            entity.offhandItem,
            if (mainHand) ItemDisplayContext.THIRD_PERSON_LEFT_HAND else ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            packedLight
        )
    }

    private fun renderHand(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        entity: LivingEntity,
        boneName: String,
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        packedLight: Int
    ) {
        if (stack.isEmpty) return
        val rightSide = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
        val processor = renderer.costumeModel.animationProcessor
        val isShield = stack.item is ShieldItem
        val bone = processor.getBone(
            if (isShield) (if (rightSide) "rightShield" else "leftShield") else boneName
        ) as GeoBone? ?: return
        val foreArm = processor.getBone(if (rightSide) "rightForeArm" else "leftForeArm") as GeoBone?

        val sideSign = if (rightSide) 1f else -1f
        // 持握位移在骨空间里的等价量：Rz(180)·Rx(-90)·Ry(180) 作用到 (side/16, 0.125, -h)
        // 得到 (side/16, -h, -0.125)。盾的物品模型自带 0.185 格偏移，前臂中点锚下取同值抵消
        val holdZ = if (isShield) 0.185f else 0.625f

        poseStack.pushPose()
        bone.parent?.let { applyBoneChain(poseStack, it, foreArm) }
        applyAttachBone(poseStack, bone, sideSign / 16f, -holdZ, -0.125f)
        poseStack.translate(bone.pivotX / 16f, bone.pivotY / 16f, bone.pivotZ / 16f)

        poseStack.mulPose(Axis.ZP.rotationDegrees(180f))
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
        poseStack.mulPose(Axis.YP.rotationDegrees(180f))
        poseStack.translate(sideSign / 16f, 0.125f, -holdZ)

        Minecraft.getInstance().entityRenderDispatcher.itemInHandRenderer
            .renderItem(entity, stack, displayContext, !rightSide, poseStack, bufferSource, packedLight)
        poseStack.popPose()
    }

    /** 正向骨链：root -> ... -> [bone] 依次应用；[foreArm] 不在链里时接到末尾 */
    private fun applyBoneChain(poseStack: PoseStack, bone: GeoBone, foreArm: GeoBone?) {
        val chain = ArrayList<GeoBone>()
        var link: GeoBone? = bone
        while (link != null) {
            chain.add(link)
            link = link.parent
        }
        chain.reverse()
        if (foreArm != null && chain.none { it === foreArm }) {
            chain.add(foreArm)
        }
        for (node in chain) RenderUtil.prepMatrixForBone(poseStack, node)
    }

    /**
     * 挂点骨自身的变换绕**握点**施加，不绕骨自己的枢轴：rightItem 的枢轴与上臂重合（肩点），
     * 照原样绕枢轴转等于拿肩关节当圆心甩物品，资产编的角度越大甩得越远
     */
    private fun applyAttachBone(poseStack: PoseStack, bone: GeoBone, gripX: Float, gripY: Float, gripZ: Float) {
        RenderUtil.translateMatrixToBone(poseStack, bone)
        val cx = bone.pivotX / 16f + gripX
        val cy = bone.pivotY / 16f + gripY
        val cz = bone.pivotZ / 16f + gripZ
        poseStack.translate(cx, cy, cz)
        RenderUtil.rotateMatrixAroundBone(poseStack, bone)
        RenderUtil.scaleMatrixForBone(poseStack, bone)
        poseStack.translate(-cx, -cy, -cz)
    }
}
