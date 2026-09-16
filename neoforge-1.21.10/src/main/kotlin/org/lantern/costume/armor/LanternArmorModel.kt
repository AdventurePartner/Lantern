package org.lantern.costume.armor

import java.util.function.Function
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.entity.EquipmentSlot
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.renderstate.CostumeRenderData

/**
 * P1 盔甲适配：按 Lantern 自定义骨架层级重建的原版盔甲模型。
 *
 * 设计要点（与历史「桥接路线」的结构性差异）：
 * 1. 自有模型实例。原版四件盔甲模型由 AvatarRenderer 全局共享，且其 setupAnim 会
 *    resetPose 全量覆盖，所以任何在提交期（layers 循环）写入部件的方案必被抹掉
 *    ——1.21.10 的 ModelFeatureRenderer.renderModel 是 setupAnim 紧接 renderToBuffer、
 *    中间零指令。本类持有自己的部件树并覆写 setupAnim，写入点即渲染前最后一步，
 *    不存在被覆盖的时序窗口。
 * 2. 部件树层级 = 骨架层级。ModelPart.translateAndRotate 是 translate(x,y,z)/16
 *    后 rotationZYX，与 GeckoLib renderBone 的递归同构，按骨架嵌套建树即可精确
 *    复现骨链：无需逐部件做 root-relative 矩阵分解，也不需要 body 的髋锚补偿
 *    （节点 pivot 直接写在髋）。
 * 3. 几何与纹理仍走原版。几何用原版 armor mesh 的 CubeListBuilder 数值重建（含
 *    deformation 膨胀），提交仍走 EquipmentLayerRenderer.renderLayers，因此纹理桶、
 *    染色、纹饰 trim、附魔光、发光轮廓全部原样继承，零复刻。
 *
 * 静态坐标换算（geo 资产坐标 -> 原版 ModelPart 坐标，逐骨验证）：x 不变、
 * y_vanilla = 24 - y_geo、z 不变。
 * 运行时姿态换算（GeoBone 渲染坐标 -> ModelPart 字段）：xRot=-rotX、yRot=-rotY、
 * zRot=+rotZ；位置增量 (posX, -posY, posZ)。
 * 依据：CostumeRenderLayer 的 T(0,1.501,0)·S(-1,-1,1) 把原版 ModelPart 空间换到
 * geo 渲染空间，S(-1,-1,1) 即绕 Z 轴 180 度，故 x/y 取负、z 保持。
 */
class LanternArmorModel private constructor(
    root: ModelPart,
    private val slot: EquipmentSlot,
    private val nodes: Map<String, ModelPart>
) : Model<HumanoidRenderState>(root, Function { RenderType.armorCutoutNoCull(it) }) {

    override fun setupAnim(state: HumanoidRenderState) {
        val playerId = (state as? AvatarRenderState)?.getRenderData(CostumeRenderData.PLAYER_UUID)
        val processor = playerId?.let(CostumeHandler::hostBones)
        if (processor == null) {
            // 非宿主化玩家：回到静态初始姿势。Mixin 已按 hostDriven 闸门过滤，
            // 正常不会走到这里；渲染器尚未建成的首帧会命中
            resetPose()
            return
        }
        // 运行时可观测：每「玩家 × 装备槽」只打一条，用于真机区分链路未接通与参数不对。
        // 四件盔甲是四个独立实例、每帧各被调多次，故按实例自身的槽位配 UUID 独立去重，
        // 不用共享帧计数取模（那种采样只会反复命中帧内固定偏移的同一个调用者）
        if (engaged.add("$playerId:$slot")) {
            org.lantern.Lantern.logger.info(
                "[Lantern][P1Armor] host pose engaged: slot={} player={}", slot, playerId
            )
        }
        // 外观整体缩放与偏移由渲染器在 geo 空间施加（GeoRenderer.scaleModelForRender
        // 与 CostumeRenderer.adjustRenderPose），盔甲不在那条链上，折进根节点补齐
        val rootPart = root()
        val rootInit = rootPart.initialPose
        val wrapper = CostumeHandler.hostWrapper(playerId)
        if (wrapper != null) {
            rootPart.xScale = wrapper.scale
            rootPart.yScale = wrapper.scale
            rootPart.zScale = wrapper.scale
            // offset 是 geo 空间的方块单位平移，换到 ModelPart 空间：x/y 取负、z 保持，再乘 16
            rootPart.x = rootInit.x() - wrapper.offsetX.toFloat() * 16f
            rootPart.y = rootInit.y() - wrapper.offsetY.toFloat() * 16f
            rootPart.z = rootInit.z() + wrapper.offsetZ.toFloat() * 16f
        } else {
            rootPart.loadPose(rootInit)
        }
        for ((boneName, part) in nodes) {
            val init = part.initialPose
            val bone = processor.getBone(boneName)
            if (bone == null) {
                part.loadPose(init)
                continue
            }
            part.x = init.x() + bone.posX
            part.y = init.y() - bone.posY
            part.z = init.z() + bone.posZ
            part.xRot = -bone.rotX
            part.yRot = -bone.rotY
            part.zRot = bone.rotZ
            part.xScale = bone.scaleX
            part.yScale = bone.scaleY
            part.zScale = bone.scaleZ
        }
    }

    companion object {
        /** 原版盔甲贴图布局（humanoid 与 humanoid_leggings 同为 64x32） */
        private const val TEX_W = 64
        private const val TEX_H = 32

        /** LayerDefinitions.OUTER_ARMOR_DEFORMATION / INNER_ARMOR_DEFORMATION */
        private val OUTER = CubeDeformation(1.0f)
        private val INNER = CubeDeformation(0.5f)

        /**
         * 腿部盔甲在 HumanoidModel.createBaseArmorMesh 里额外 extend(-0.1)。
         * 护腿是四件套里唯一跨关节的：纹理覆盖 geo y 12..3 而膝关节在 geo y 6，
         * 故必须按膝切成大腿 6px + 小腿 6px 两段分挂两根骨。两段若各自沿 y 膨胀，
         * 会在切口处产生共面 z-fight，故 y 轴不膨胀；代价是护腿顶面与底面少 0.4px
         * 外扩，分别被躯干甲与靴子完全遮挡，不可见。
         */
        private val LEGGINGS_LEG = CubeDeformation(0.4f, 0.0f, 0.4f)

        /** 靴子几何是整条腿，但纹理只在小腿段不透明，故整块挂小腿骨即像素级正确 */
        private val BOOT_LEG = CubeDeformation(0.9f)

        private val cache = java.util.EnumMap<EquipmentSlot, LanternArmorModel>(EquipmentSlot::class.java)

        /** 一次性接通标记的去重集（键为 玩家:槽位），随模型重建一并清空 */
        private val engaged = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        /**
         * 按装备槽取模型（四件各一份）。实例可全局共享：setupAnim 在每次 submitModel
         * 的渲染瞬间重算且紧接 renderToBuffer，多玩家多次提交互不污染
         */
        @JvmStatic
        @Synchronized
        fun of(slot: EquipmentSlot): LanternArmorModel = cache.getOrPut(slot) { build(slot) }

        /** 资源重载时丢弃重建，与 vanilla 模型生命周期对齐 */
        @JvmStatic
        @Synchronized
        fun invalidate() {
            cache.clear()
            engaged.clear()
        }

        private fun build(slot: EquipmentSlot): LanternArmorModel {
            val mesh = MeshDefinition()
            val meshRoot = mesh.root

            fun empty() = CubeListBuilder.create()

            // 各槽位几何分配（依据 HumanoidModel.createArmorMeshSet 的 retainParts）：
            // HEAD  = head + hat 外壳
            // CHEST = body + 双大臂（袖子纹理只覆盖 geo y 24..19，肘在 18，整块挂大臂）
            // LEGS  = body + 双腿按膝切两段（唯一跨关节件）
            // FEET  = 双小腿（几何为整条腿，大腿段纹理透明）
            val headCubes = if (slot == EquipmentSlot.HEAD) {
                CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, OUTER)
                    .texOffs(32, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, OUTER.extend(0.5f))
            } else empty()

            val bodyCubes = when (slot) {
                EquipmentSlot.CHEST ->
                    CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0f, -12.0f, -2.0f, 8.0f, 12.0f, 4.0f, OUTER)
                EquipmentSlot.LEGS ->
                    CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0f, -12.0f, -2.0f, 8.0f, 12.0f, 4.0f, INNER)
                else -> empty()
            }

            val rightArmCubes = if (slot == EquipmentSlot.CHEST) {
                CubeListBuilder.create().texOffs(40, 16)
                    .addBox(-3.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, OUTER)
            } else empty()
            val leftArmCubes = if (slot == EquipmentSlot.CHEST) {
                CubeListBuilder.create().texOffs(40, 16).mirror()
                    .addBox(-1.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, OUTER)
            } else empty()

            // 护腿上段（大腿）：原版腿盒 UV 的上半，与身体 geo 的 rightLeg uv[0,16] 同偏移
            fun upperLeg(mirrored: Boolean): CubeListBuilder {
                if (slot != EquipmentSlot.LEGS) return empty()
                val builder = CubeListBuilder.create().texOffs(0, 16)
                if (mirrored) builder.mirror()
                return builder.addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f, LEGGINGS_LEG)
            }

            // 小腿节点：护腿下段取 UV 下半（对应身体 geo 的 rightForeLeg uv[0,22]）；靴子整条腿
            fun lowerLeg(mirrored: Boolean): CubeListBuilder = when (slot) {
                EquipmentSlot.LEGS -> {
                    val builder = CubeListBuilder.create().texOffs(0, 22)
                    if (mirrored) builder.mirror()
                    builder.addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f, LEGGINGS_LEG)
                }
                EquipmentSlot.FEET -> {
                    val builder = CubeListBuilder.create().texOffs(0, 16)
                    if (mirrored) builder.mirror()
                    builder.addBox(-2.0f, -6.0f, -2.0f, 4.0f, 12.0f, 4.0f, BOOT_LEG)
                }
                else -> empty()
            }

            // 层级严格镜像 player_default.geo.json 的骨架。每个节点的 PartPose 是
            // 「本骨 vanilla 坐标 - 父骨 vanilla 坐标」，vanilla 坐标 = (x_geo, 24 - y_geo, z_geo)
            val root = meshRoot.addOrReplaceChild("root", empty(), PartPose.offset(0.0f, 24.0f, 0.0f))
            val waist = root.addOrReplaceChild("waist", empty(), PartPose.offset(0.0f, -12.0f, 0.0f))
            val body = waist.addOrReplaceChild("body", bodyCubes, PartPose.ZERO)
            body.addOrReplaceChild("head", headCubes, PartPose.offset(0.0f, -12.0f, 0.0f))

            val rightArm = body.addOrReplaceChild("rightArm", rightArmCubes, PartPose.offset(-5.0f, -12.0f, 0.0f))
            rightArm.addOrReplaceChild("rightForeArm", empty(), PartPose.offset(0.0f, 6.0f, 0.0f))
            val leftArm = body.addOrReplaceChild("leftArm", leftArmCubes, PartPose.offset(5.0f, -12.0f, 0.0f))
            leftArm.addOrReplaceChild("leftForeArm", empty(), PartPose.offset(0.0f, 6.0f, 0.0f))

            val rightLeg = waist.addOrReplaceChild("rightLeg", upperLeg(false), PartPose.offset(-1.9f, 0.0f, 0.0f))
            rightLeg.addOrReplaceChild("rightForeLeg", lowerLeg(false), PartPose.offset(0.0f, 6.0f, 0.0f))
            val leftLeg = waist.addOrReplaceChild("leftLeg", upperLeg(true), PartPose.offset(1.9f, 0.0f, 0.0f))
            leftLeg.addOrReplaceChild("leftForeLeg", lowerLeg(true), PartPose.offset(0.0f, 6.0f, 0.0f))

            val baked = LayerDefinition.create(mesh, TEX_W, TEX_H).bakeRoot()
            val rootPart = baked.getChild("root")
            val waistPart = rootPart.getChild("waist")
            val bodyPart = waistPart.getChild("body")
            val rightArmPart = bodyPart.getChild("rightArm")
            val leftArmPart = bodyPart.getChild("leftArm")
            val rightLegPart = waistPart.getChild("rightLeg")
            val leftLegPart = waistPart.getChild("leftLeg")

            // root 节点不参与逐骨写入：它承载外观 scale/offset，由 setupAnim 单独裁决
            val nodes = linkedMapOf(
                "waist" to waistPart,
                "body" to bodyPart,
                "head" to bodyPart.getChild("head"),
                "rightArm" to rightArmPart,
                "rightForeArm" to rightArmPart.getChild("rightForeArm"),
                "leftArm" to leftArmPart,
                "leftForeArm" to leftArmPart.getChild("leftForeArm"),
                "rightLeg" to rightLegPart,
                "rightForeLeg" to rightLegPart.getChild("rightForeLeg"),
                "leftLeg" to leftLegPart,
                "leftForeLeg" to leftLegPart.getChild("leftForeLeg")
            )
            return LanternArmorModel(baked, slot, nodes)
        }
    }
}
