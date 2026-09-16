package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderstate.CostumeRenderContext
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.base.GeoRenderState
import software.bernie.geckolib.util.RenderUtil

/**
 * P1 玩家宿主化：手持物品挂到整替模型的 rightItem/leftItem 挂点骨。
 *
 * 变换链设计（完整适配，非特殊挂载）：
 * 1. 正向骨链矩阵：root -> ... -> 挂点骨依次 prepMatrixForBone。GeckoLib 5.3 的骨矩阵
 *    是纯增量（静止恒等，pivot 不进矩阵），链后必须 translate(挂点骨 pivot) 才是手腕位置。
 * 2. 空间适配 rotZ(180°)：本渲染器处于恒等空间（vanilla 的 S(-1,-1,1) 已被
 *    CostumeRenderLayer 的补偿撤销），而 vanilla 持握变换编制于 ModelPart 空间——
 *    rotZ(180°)=S(-1,-1,1)（det=+1，无镜像伪影）补回同构空间。
 * 3. vanilla 第三人称持握变换（ItemInHandLayer.submitArmWithItem 字节码同序）：
 *    rotX(-90°) -> rotY(180°) -> translate(side/16, 0.125, -0.625)。
 * 4. display 解析走 updateForLiving（带实体——vanilla 第三人称同款通道，
 *    罗盘等实体相关模型与 vanilla 行为一致）。
 */
class CostumeItemLayer(
    private val renderer: CostumeRenderer
) {
    private val mainState = ItemStackRenderState()
    private val offState = ItemStackRenderState()

    fun submitItems(
        poseStack: PoseStack,
        renderTasks: SubmitNodeCollector,
        context: CostumeRenderContext,
        packedLight: Int
    ) {
        val entity = context.entity as? LivingEntity ?: return
        val mainHand = entity.mainArm == HumanoidArm.RIGHT
        // 主手物品挂惯用手侧的骨（右撇子主手 -> rightItem），display 上下文跟骨走。
        // 盾走前臂中段绑缚挂点（防御姿态跟随前臂），其余物品走手持链（肩锚）
        submitHand(
            poseStack, renderTasks, entity,
            if (mainHand) "rightItem" else "leftItem",
            entity.mainHandItem,
            if (mainHand) ItemDisplayContext.THIRD_PERSON_RIGHT_HAND else ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
            packedLight, mainState
        )
        submitHand(
            poseStack, renderTasks, entity,
            if (mainHand) "leftItem" else "rightItem",
            entity.offhandItem,
            if (mainHand) ItemDisplayContext.THIRD_PERSON_LEFT_HAND else ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            packedLight, offState
        )
    }

    private fun submitHand(
        poseStack: PoseStack,
        renderTasks: SubmitNodeCollector,
        entity: LivingEntity,
        boneName: String,
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        packedLight: Int,
        state: ItemStackRenderState
    ) {
        if (stack.isEmpty) return
        val mc = Minecraft.getInstance()
        val rightSide = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND

        // 盾：挂点换到前臂中段绑缚骨（防御姿态跟随前臂），变换链与手持完全同构——
        // vanilla 持盾姿态本就是手持竖握，贴前臂竖直即为绑缚视觉，不自造姿态旋转
        val bone = if (stack.item is net.minecraft.world.item.ShieldItem) {
            renderer.geoModel.animationProcessor.getBone(if (rightSide) "rightShield" else "leftShield") ?: return
        } else {
            renderer.geoModel.animationProcessor.getBone(boneName) ?: return
        }

        poseStack.pushPose()
        // 1. 正向骨链（与 renderBone 递归渲染同序）+ 挂点骨 pivot
        applyBoneChain(poseStack, bone)
        poseStack.translate(bone.pivotX / 16f, bone.pivotY / 16f, bone.pivotZ / 16f)

        // 2. 恒等空间 -> ModelPart 同构空间（vanilla 持握链的编制空间）
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f))

        // 3. vanilla 第三人称持握旋转（ItemInHandLayer.submitArmWithItem 同序）
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
        poseStack.mulPose(Axis.YP.rotationDegrees(180f))
        // 持握位移 (side/16, 0.125, -0.625) 的设计语境是【从肩锚出发】（0.625=肩到手心）。
        // 盾是 BER（ShieldSpecialRenderer），自带 +0.185 格内置偏移；前臂中点锚下
        // 盾专用 z=-0.185（实测三点评定的折算：z 分量与世界 y 贡献数值相等），
        // 恰好抵消 BER 偏移，盾体落回锚点（前臂中段）
        val isShield = stack.item is net.minecraft.world.item.ShieldItem
        val side = if (rightSide) 1f else -1f
        poseStack.translate(side / 16f, 0.125f, if (isShield) -0.185f else -0.625f)

        // 4. display 解析（vanilla 第三人称同款通道）
        mc.itemModelResolver.updateForLiving(state, stack, displayContext, entity)
        state.submit(poseStack, renderTasks, packedLight, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()
    }

    /** 正向骨链：root -> ... -> 挂点骨依次应用（与 renderBone 递归渲染同序） */
    private fun applyBoneChain(poseStack: PoseStack, bone: GeoBone) {
        val chain = ArrayList<GeoBone>()
        var link: GeoBone? = bone
        while (link != null) {
            chain.add(link)
            link = link.parent
        }
        chain.reverse()
        for (l in chain) RenderUtil.prepMatrixForBone(poseStack, l)
    }
}
