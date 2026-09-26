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
 *
 *    骨链里要补一段前臂：geo 里 rightItem/leftItem 挂在**上臂**下、与前臂同级
 *    （rightItem <- rightArm <- body），枢轴还与上臂重合（肩点），肘部弯曲整段
 *    不被继承。直臂剪辑（默认外观的待机/左砍/右砍，前臂恒 0）看不出差别，
 *    一旦剪辑折肘就露馅：重武器待机折肘 72.5 度，不补前臂时大剑飘在身前，
 *    补上之后才真的扛到肩上。
 * 1b. 挂点骨自身的旋转与缩放绕**握点**施加，不绕它自己的枢轴（见 applyAttachBone）。
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
        val processor = renderer.geoModel.animationProcessor
        val isShieldItem = stack.item is net.minecraft.world.item.ShieldItem
        val bone = if (isShieldItem) {
            processor.getBone(if (rightSide) "rightShield" else "leftShield") ?: return
        } else {
            processor.getBone(boneName) ?: return
        }
        // 前臂：手部挂点骨跳过了它（盾挂点本就在前臂下，链里已有，插入时会跳过重复）
        val foreArm = processor.getBone(if (rightSide) "rightForeArm" else "leftForeArm")

        val sideSign = if (rightSide) 1f else -1f
        // 持握位移在骨空间里的等价量：Rz(180)·Rx(-90)·Ry(180) 作用到
        // (side/16, 0.125, -h) 上得到 (side/16, -h, -0.125)。挂点骨的旋转绕这个点转
        val holdZ = if (isShieldItem) 0.185f else 0.625f

        poseStack.pushPose()
        // 1. 正向骨链到挂点骨的父级（含前臂）；挂点骨自身单独处理
        bone.parent?.let { applyBoneChain(poseStack, it, foreArm) }
        applyAttachBone(poseStack, bone, sideSign / 16f, -holdZ, -0.125f)
        poseStack.translate(bone.pivotX / 16f, bone.pivotY / 16f, bone.pivotZ / 16f)

        // 2. 恒等空间 -> ModelPart 同构空间（vanilla 持握链的编制空间）
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f))

        // 3. vanilla 第三人称持握旋转（ItemInHandLayer.submitArmWithItem 同序）
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
        poseStack.mulPose(Axis.YP.rotationDegrees(180f))
        // 0.625 = 肩到手心；盾是 BER（ShieldSpecialRenderer）自带 +0.185 格内置偏移，
        // 前臂中点锚下取 z=-0.185 恰好抵消，盾体落回锚点（前臂中段）
        poseStack.translate(sideSign / 16f, 0.125f, -holdZ)

        // 4. display 解析（vanilla 第三人称同款通道）
        mc.itemModelResolver.updateForLiving(state, stack, displayContext, entity)
        state.submit(poseStack, renderTasks, packedLight, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()
    }

    /**
     * 正向骨链：root -> ... -> [bone] 依次应用（与 renderBone 递归渲染同序）。
     *
     * [foreArm] 不在链里时接到末尾：手部挂点骨在 geo 里与前臂同级而非其子级，
     * 不补这一段，肘部一弯物品就留在上臂的延长线上（重武器待机折肘 72.5 度，
     * 不补就扛不到肩上）。盾挂点本来就在前臂之下，链里已有，不重复接
     */
    private fun applyBoneChain(poseStack: PoseStack, bone: GeoBone, foreArm: GeoBone? = null) {
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
        for (l in chain) RenderUtil.prepMatrixForBone(poseStack, l)
    }

    /**
     * 挂点骨自身的变换：旋转与缩放绕**握点**施加，而不是绕骨自己的枢轴。
     *
     * geo 里 rightItem/leftItem 的枢轴与上臂完全重合（肩点），离实际握点 10px。
     * 照 prepMatrixForBone 原样绕枢轴转，等于拿肩关节当圆心甩物品——资产在挂点骨
     * 上编的角度越大甩得越远：重武器待机恒 -22.5 度把剑甩离手 3.9px，
     * 剑仙普攻编到 ±180 度，直接甩出 14~17px，整把剑脱手。
     *
     * 旋转中心挪到握点之后，挂点骨只负责朝向，位置完全由骨链决定：实测三套动画库
     * 十一段剪辑的落点距腕端恒为 3.0px（= 默认外观全程的基准值）。
     * 朝向仍按骨空间的原轴施加，资产编的角度观感不变，只是不再附带位移
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
