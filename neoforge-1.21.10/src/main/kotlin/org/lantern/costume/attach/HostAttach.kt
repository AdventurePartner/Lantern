package org.lantern.costume.attach

import com.mojang.blaze3d.vertex.PoseStack
import java.util.UUID
import net.minecraft.client.model.geom.ModelPart
import org.joml.Matrix3f
import org.joml.Vector3f
import org.lantern.costume.handler.CostumeHandler
import software.bernie.geckolib.animatable.processing.AnimationProcessor
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.util.RenderUtil

/**
 * P1 附属渲染层适配：把原版各装饰层从「贴在隐形的原版骨架上」改为跟随 Lantern 骨骼。
 *
 * 原版玩家渲染器共挂十层，整替后它们分两类：
 * 一类靠 parentModel 的 ModelPart 定位（CustomHeadLayer 的头颅、ArrowLayer 的插箭、
 * BeeStingerLayer 的蜂刺），对这类把原版 PlayerModel 的部件本身写成骨骼姿态即可，
 * 定位逻辑不动就自动跟随（主体模型本身已被压制不渲染，改它的部件无视觉副作用）；
 * 另一类持有独立模型、直接用进入时的 poseStack（CapeLayer 的披风、WingsLayer 的鞘翅、
 * ParrotOnShoulderLayer 的鹦鹉、Deadmau5EarsLayer 的耳朵），对这类把 poseStack 整体
 * 变换到对应骨骼驱动后的空间即可。
 *
 * 两类共用同一个空间换算：设进入时 poseStack 处于原版 ModelPart 空间 V，
 * CostumeRenderLayer 用 C = T(0,1.501,0)·S(-1,-1,1) 把它换到 geo 渲染空间 G。
 * 某骨的骨链变换 M 在 G 空间成立，故在 V 空间的等效变换是相似变换 C·M·C⁻¹，
 * 其中 C⁻¹ = S(-1,-1,1)·T(0,-1.501,0)。骨链恒等时该式退化为恒等（残差 0.001 格，
 * 即原版 1.501 与 geo 1.5 的历史差），所以静止姿态与原版逐像素一致。
 */
object HostAttach {

    /** 原版模型原点（颈）在 geo 骨架坐标里的高度，单位像素 */
    private const val VANILLA_ORIGIN_GEO_Y = 24.0f

    /** 原版 LivingEntityRenderer 的 translate(0,-1.501,0)，CostumeRenderLayer 以同值反向补偿 */
    private const val COMPENSATION = 1.501f

    @JvmStatic
    fun bones(playerId: UUID): AnimationProcessor<*>? = CostumeHandler.hostBones(playerId)

    /**
     * 把 poseStack 变换到指定骨骼驱动后的「原版模型空间」——原点落在该骨带动下的
     * 颈部位置、朝向与原版一致，于是持有独立模型的层不改一行定位代码即跟随骨骼。
     *
     * @return 是否成功（骨骼缺失时不变换，调用方应走原版路径）
     */
    @JvmStatic
    fun applyBoneSpace(poseStack: PoseStack, processor: AnimationProcessor<*>, boneName: String): Boolean {
        val bone = processor.getBone(boneName) ?: return false
        poseStack.translate(0.0f, COMPENSATION, 0.0f)
        poseStack.scale(-1.0f, -1.0f, 1.0f)
        applyBoneChain(poseStack, bone)
        // 骨链作用在 geo 模型原点（脚底）上，把原点抬到原版模型原点（颈）所在高度
        poseStack.translate(0.0f, VANILLA_ORIGIN_GEO_Y / 16.0f, 0.0f)
        poseStack.scale(-1.0f, -1.0f, 1.0f)
        return true
    }

    /**
     * 把某根骨的完整骨链变换写进一个原版 ModelPart。
     *
     * 原版六部件都是 root 的同级子部件（扁平），而 Lantern 骨架是嵌套的
     * （root→waist→body→head/臂，腿挂 waist），层级与枢轴都不同，所以不能抄旋转，
     * 必须把整条骨链合成后再分解到部件的 T·R 上——这正是历史桥接路线漏掉的一步。
     */
    @JvmStatic
    fun writePart(part: ModelPart, processor: AnimationProcessor<*>, boneName: String) {
        val bone = processor.getBone(boneName) ?: return
        // 目标：让部件的 translateAndRotate 等于该骨的局部坐标系。骨链以
        // translateAwayFromPivotPoint 收尾（原点仍在模型原点），故链后要补 translate(pivot)
        // 才落到骨自身的枢轴，再翻回原版朝向——与 CostumeItemLayer 的手持挂点同构
        val stack = PoseStack()
        stack.translate(0.0f, COMPENSATION, 0.0f)
        stack.scale(-1.0f, -1.0f, 1.0f)
        applyBoneChain(stack, bone)
        stack.translate(bone.pivotX / 16.0f, bone.pivotY / 16.0f, bone.pivotZ / 16.0f)
        stack.scale(-1.0f, -1.0f, 1.0f)

        val pose = stack.last().pose()
        val translation = pose.getTranslation(Vector3f())
        val scale = pose.getScale(Vector3f())

        // 分解出纯旋转：先除掉缩放，再取 ZYX 欧拉角（与 ModelPart.rotateBy 同法）
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

    /** 正向骨链：root -> ... -> 目标骨依次应用（与 GeckoLib renderBone 的递归渲染同序） */
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
