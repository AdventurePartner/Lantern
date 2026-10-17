package org.lantern.costume.attach

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.joml.Matrix3f
import org.joml.Vector3f
import org.lantern.costume.handler.CostumeHandler
import software.bernie.geckolib.animation.AnimationProcessor
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.util.RenderUtil

/**
 * hostDriven 外观下原版玩家渲染的挂钩状态与空间换算（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1；
 * 1.21.10 有同名副本，走 RenderState 不走这里的 begin/end）。
 *
 * 一次玩家渲染内（LivingEntityRenderer.render HEAD 到 RETURN）：
 * - 身体压制：getRenderType 返回 null，原版身体不提交
 * - 靠 parentModel 的 ModelPart 定位的层（头颅、插箭、蜂刺、盔甲）：setupAnim TAIL 把原版
 *   PlayerModel 的部件写成骨骼姿态（writeHostPose），渲染结束还原
 * - 持有独立模型、直接用进入时 poseStack 的层（披风、鞘翅、肩上鹦鹉、deadmau5 耳朵）：
 *   把 poseStack 整体换到该骨驱动后的原版模型空间（enterBoneSpace）
 *
 * 空间换算：进入时 poseStack 处于原版 ModelPart 空间 V，CostumeRenderLayer 用
 * C = T(0,1.501,0)·S(-1,-1,1) 换到 geo 渲染空间 G，骨链变换 M 在 V 空间的等效是 C·M·C⁻¹。
 * 骨链恒等时退化为恒等，静止姿态与原版逐像素一致
 */
object HostAttach {

    /** 原版模型原点（颈）在 geo 骨架坐标里的高度，单位像素 */
    private const val VANILLA_ORIGIN_GEO_Y = 24.0f

    /** 原版 LivingEntityRenderer 的 translate(0,-1.501,0)，CostumeRenderLayer 以同值反向补偿 */
    private const val COMPENSATION = 1.501f

    private var activeEntity: Entity? = null
    private var activeBones: AnimationProcessor<*>? = null
    private var writtenModel: HumanoidModel<*>? = null

    /**
     * 玩家渲染入口（LivingEntityRenderer.render HEAD）：hostDriven 整替外观可用时算好本帧姿势，
     * 本次渲染期间的 setupAnim、附属层与身体压制都读这里的状态
     */
    @JvmStatic
    fun begin(entity: Entity) {
        reset()
        if (entity !is Player || entity.isInvisible) return
        val processor = CostumeHandler.prepareHost(entity) ?: return
        activeEntity = entity
        activeBones = processor
    }

    /** 玩家渲染出口（RETURN）：还原写过的原版部件 */
    @JvmStatic
    fun end(entity: Entity) {
        if (activeEntity === entity) reset()
    }

    private fun reset() {
        writtenModel?.let(::restoreParts)
        writtenModel = null
        activeEntity = null
        activeBones = null
    }

    /** 该实体正处于 hostDriven 渲染中时返回其骨骼处理器 */
    @JvmStatic
    fun bones(entity: Entity): AnimationProcessor<*>? =
        if (activeEntity === entity) activeBones else null

    /** PlayerModel.setupAnim TAIL：把骨骼姿态写进原版六部件，供头颅、插箭、盔甲等按部件定位的层跟随 */
    @JvmStatic
    fun writeHostPose(model: HumanoidModel<*>, entity: Entity) {
        val processor = bones(entity) ?: return
        writePart(model.head, processor, "head")
        writePart(model.body, processor, "body")
        // 原版四肢单段、Lantern 两段：部件挂大臂 / 大腿骨，肘膝以下由整替模型自身承担
        writePart(model.rightArm, processor, "rightArm")
        writePart(model.leftArm, processor, "leftArm")
        writePart(model.rightLeg, processor, "rightLeg")
        writePart(model.leftLeg, processor, "leftLeg")
        writtenModel = model
    }

    /**
     * 持有独立模型的附属层 HEAD 调用：压栈并换到骨骼空间。
     * 返回 true 时调用方必须在 RETURN 处 popPose
     */
    @JvmStatic
    fun enterBoneSpace(poseStack: PoseStack, entity: Entity, boneName: String): Boolean {
        val bone = bones(entity)?.getBone(boneName) as GeoBone? ?: return false
        poseStack.pushPose()
        poseStack.translate(0.0f, COMPENSATION, 0.0f)
        poseStack.scale(-1.0f, -1.0f, 1.0f)
        applyBoneChain(poseStack, bone)
        // 骨链作用在 geo 模型原点（脚底）上，把原点抬到原版模型原点（颈）所在高度
        poseStack.translate(0.0f, VANILLA_ORIGIN_GEO_Y / 16.0f, 0.0f)
        poseStack.scale(-1.0f, -1.0f, 1.0f)
        return true
    }

    /**
     * 1.21.1 / 1.20.1 的 setupAnim 只覆盖部分字段（头身的 x/z、zRot、缩放都不复位，1.21.10 会整体复位），
     * 本玩家渲染完必须把写过的部件还原，否则姿势会漏给下一个渲染的玩家与第一人称手臂
     */
    private fun restoreParts(model: HumanoidModel<*>) {
        for (part in arrayOf(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg)) {
            part.resetPose()
            part.xScale = 1.0f
            part.yScale = 1.0f
            part.zScale = 1.0f
        }
    }

    /**
     * 把某根骨的完整骨链变换写进一个原版 ModelPart。原版六部件是 root 的同级子部件（扁平），
     * Lantern 骨架是嵌套的（root→waist→body→head/臂，腿挂 waist），层级与枢轴都不同，
     * 所以不能抄旋转，必须把整条骨链合成后再分解到部件的 T·R·S 上
     */
    private fun writePart(part: ModelPart, processor: AnimationProcessor<*>, boneName: String) {
        val bone = processor.getBone(boneName) as GeoBone? ?: return
        val stack = PoseStack()
        stack.translate(0.0f, COMPENSATION, 0.0f)
        stack.scale(-1.0f, -1.0f, 1.0f)
        applyBoneChain(stack, bone)
        // 骨链以 translateAwayFromPivotPoint 收尾，原点仍在模型原点，补上枢轴才落到骨自身的坐标系
        stack.translate(bone.pivotX / 16.0f, bone.pivotY / 16.0f, bone.pivotZ / 16.0f)
        stack.scale(-1.0f, -1.0f, 1.0f)

        val pose = stack.last().pose()
        val translation = pose.getTranslation(Vector3f())
        val scale = pose.getScale(Vector3f())

        // 分解出纯旋转：先除掉缩放，再取 ZYX 欧拉角（与 ModelPart 的旋转顺序一致）
        val basis = Matrix3f(pose)
        if (scale.x != 0.0f && scale.y != 0.0f && scale.z != 0.0f) {
            basis.scale(1.0f / scale.x, 1.0f / scale.y, 1.0f / scale.z)
        }
        val euler = basis.getEulerAnglesZYX(Vector3f())

        part.setPos(translation.x * 16.0f, translation.y * 16.0f, translation.z * 16.0f)
        part.setRotation(euler.x, euler.y, euler.z)
        part.xScale = scale.x
        part.yScale = scale.y
        part.zScale = scale.z
    }

    /** 正向骨链：root -> ... -> 目标骨依次应用（与 GeckoLib renderRecursively 同序） */
    private fun applyBoneChain(poseStack: PoseStack, bone: GeoBone) {
        val chain = ArrayList<GeoBone>()
        var link: GeoBone? = bone
        while (link != null) {
            chain.add(link)
            link = link.parent
        }
        chain.reverse()
        for (node in chain) RenderUtil.prepMatrixForBone(poseStack, node)
    }
}
