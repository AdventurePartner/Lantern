package org.lantern.worldimage

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.Util
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lantern.core.image.ImageAnims
import org.lantern.core.image.ImagePose

/**
 * 世界图片渲染器的 1.20.1 版本（同 FQN 替换根源码，根源码在 build.gradle.kts 里 exclude）。
 *
 * 与 1.21.x 版的唯一差异是 VertexConsumer 的提交链：1.20.1 是
 * vertex().color().uv().uv2().endVertex()（float 分量），1.21.1+ 是
 * addVertex().setColor().setUv().setLight()。其余逻辑与根源码逐行一致。
 */
object WorldImageRenderer {

    private const val FULL_LIGHT = 0xF000F0

    fun render(
        poseStack: PoseStack,
        buffers: MultiBufferSource.BufferSource,
        camera: Camera,
        partialTick: Float
    ) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val now = Util.getMillis()
        val instances = WorldImageManager.activeInstances(now)
        if (instances.isEmpty()) return

        val firstPerson = client.options.getCameraType().isFirstPerson
        val camPos = camera.getPosition()
        val font = client.font

        for (instance in instances) {
            val spec = instance.spec
            var boundEntity: Entity? = null
            val baseX: Double
            val baseY: Double
            val baseZ: Double
            when (instance.bind) {
                ImageBind.PLAYER -> {
                    val player = level.players().firstOrNull { it.name.string == instance.bindPlayer }
                    if (player == null) continue
                    boundEntity = player
                    val p = player.getPosition(partialTick)
                    baseX = p.x; baseY = p.y; baseZ = p.z
                }
                ImageBind.ENTITY -> {
                    val entity = level.entitiesForRendering().firstOrNull { it.uuid == instance.bindEntity }
                    if (entity == null) continue
                    boundEntity = entity
                    val p = entity.getPosition(partialTick)
                    baseX = p.x; baseY = p.y; baseZ = p.z
                }
                ImageBind.POSITION -> {
                    if (instance.bindWorld != null &&
                        level.dimension().location().toString() != instance.bindWorld
                    ) continue
                    baseX = instance.bindX; baseY = instance.bindY; baseZ = instance.bindZ
                }
            }

            // 绑定自己 + 第一人称 + first-person=false = 隐藏（参考实现 only_third_person 的泛化）
            if (!spec.firstPersonVisible && firstPerson && boundEntity === client.player) continue

            val ticks = (now - instance.spawnAtMillis) / 50.0
            val pose = ImageAnims.sample(instance.anim, ticks)
            val alpha = pose.alpha.coerceIn(0.0, 1.0)

            // 世界对齐、相机相对的中心点（基础偏移 + 动画偏移）
            val centerX = (baseX - camPos.x + spec.offsetX + pose.offsetX).toFloat()
            val centerY = (baseY - camPos.y + spec.offsetY + pose.offsetY).toFloat()
            val centerZ = (baseZ - camPos.z + spec.offsetZ + pose.offsetZ).toFloat()

            // 面片基向量（世界系）：billboard 取相机右/上；fixed 按 yaw/pitch 组合，动画旋转随后叠加
            val rotation = orientation(camera, spec, pose)
            val right = Vector3f(1.0f, 0.0f, 0.0f).rotate(rotation)
            val up = Vector3f(0.0f, 1.0f, 0.0f).rotate(rotation)

            val scale = spec.scale * pose.scale
            val matrix = poseStack.last().pose()
            when (spec.type) {
                ImageType.TEXTURE -> drawTexture(matrix, buffers, spec, scale, alpha, centerX, centerY, centerZ, right, up)
                ImageType.TEXT -> drawText(matrix, buffers, font, spec, scale, alpha, centerX, centerY, centerZ, rotation)
            }
        }
    }

    /**
     * 面片朝向四元数：BILLBOARD = 相机旋转（世界→视角，拷贝后可安全叠加增量）；
     * FIXED = 实体 yaw/pitch 同语义（yaw 0 面向 +Z）；动画 rot 增量叠加在后——
     * billboard 下即"面向观察者的同时自旋"
     */
    private fun orientation(camera: Camera, spec: WorldImageSpec, pose: ImagePose): Quaternionf {
        val rotation = when (spec.facing) {
            ImageFacing.BILLBOARD -> Quaternionf(camera.rotation())
            ImageFacing.FIXED -> Quaternionf()
                .rotationY(Math.toRadians(-spec.rotYaw).toFloat())
                .rotateX(Math.toRadians(spec.rotPitch).toFloat())
        }
        if (pose.rotYaw != 0.0) rotation.rotateY(Math.toRadians(-pose.rotYaw).toFloat())
        if (pose.rotPitch != 0.0) rotation.rotateX(Math.toRadians(pose.rotPitch).toFloat())
        return rotation
    }

    /** 贴图面片：RenderType.text 走名牌渲染管线（混合 + 顶点色 alpha），FULL_LIGHT 全亮。 */
    private fun drawTexture(
        matrix: Matrix4f,
        buffers: MultiBufferSource,
        spec: WorldImageSpec,
        scale: Double,
        alpha: Double,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        right: Vector3f,
        up: Vector3f
    ) {
        val texture = spec.texture ?: return
        val renderType = if (spec.seeThrough) RenderType.textSeeThrough(texture) else RenderType.text(texture)
        val consumer = buffers.getBuffer(renderType)
        val half = (spec.size * scale / 2.0).toFloat()
        val a = alpha.toFloat()
        vertex(consumer, matrix, centerX, centerY, centerZ, right, up, -half, -half, 0.0f, 1.0f, a)
        vertex(consumer, matrix, centerX, centerY, centerZ, right, up, half, -half, 1.0f, 1.0f, a)
        vertex(consumer, matrix, centerX, centerY, centerZ, right, up, half, half, 1.0f, 0.0f, a)
        vertex(consumer, matrix, centerX, centerY, centerZ, right, up, -half, half, 0.0f, 0.0f, a)
    }

    /**
     * 原版字体文字：drawInBatch 复用文字批处理（支持投影/穿墙模式）。
     * size 为字符行高（9px 行高映射为 size 格），水平按 font.width 居中。
     * 字体局部 y 向下：基向量 (right, -up, -forward) 等于朝向四元数再绕 X 翻 180°，
     * 所以文本矩阵直接用 rotate(朝向.copy.rotateX(PI)) 表达
     */
    private fun drawText(
        baseMatrix: Matrix4f,
        buffers: MultiBufferSource,
        font: Font,
        spec: WorldImageSpec,
        scale: Double,
        alpha: Double,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        rotation: Quaternionf
    ) {
        val textScale = (spec.size * scale / 9.0).toFloat()
        val matrix = Matrix4f(baseMatrix)
            .translate(centerX, centerY, centerZ)
            .rotate(Quaternionf(rotation).rotateX(Math.PI.toFloat()))
            .scale(textScale, textScale, textScale)
        val width = font.width(spec.text).toFloat()
        val color = ((alpha * 255).toInt() shl 24) or (spec.color and 0x00FFFFFF)
        font.drawInBatch(
            spec.text,
            -width / 2.0f,
            -4.5f,
            color,
            true,
            matrix,
            buffers,
            if (spec.seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL,
            0,
            FULL_LIGHT
        )
    }

    /** 单顶点：中心 + 基向量张开的偏移，经基矩阵手工变换后用 1.20.1 的提交链。 */
    private fun vertex(
        consumer: VertexConsumer,
        matrix: Matrix4f,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        right: Vector3f,
        up: Vector3f,
        rx: Float,
        uy: Float,
        u: Float,
        v: Float,
        alpha: Float
    ) {
        val point = Vector3f(
            centerX + right.x * rx + up.x * uy,
            centerY + right.y * rx + up.y * uy,
            centerZ + right.z * rx + up.z * uy
        )
        matrix.transformPosition(point)
        // 1.20.1 的 VertexConsumer.vertex 吃 double 分量
        consumer.vertex(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
            .color(1.0f, 1.0f, 1.0f, alpha)
            .uv(u, v).uv2(FULL_LIGHT).endVertex()
    }
}
