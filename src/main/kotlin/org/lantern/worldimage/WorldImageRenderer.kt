package org.lantern.worldimage

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.Util
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lantern.Lantern
import org.lantern.core.image.ImageAnims
import org.lantern.core.image.ImagePose
import org.lantern.internal.handler.TextureHandler
import org.lantern.platform.IdentifierBridge

/**
 * 世界图片的统一绘制入口，四平台的每帧世界渲染钩子都调到这里。
 *
 * 坐标系约定（三代管线字节码实证一致）：实体阶段的 posestack 为 identity、
 * 相机旋转在全局 RenderSystem 矩阵——钩子一律传空 PoseStack，顶点以
 * "世界对齐、相机原点"坐标书写，billboard 用相机右/上基向量张成四边形
 * （`mulPose(camera.rotation())` 会与全局矩阵二次旋转，禁用）。
 * 贴图与文字写入共享 BufferSource 后由 renderLevel 末尾的无参 endBatch
 * 兜底提交，钩子不做任何 flush（AFTER_ENTITIES 时点原版尚有未完成批次，
 * 提前全刷会改变绘制顺序）。
 * 时间轴用真实毫秒换算（50ms = 1 tick），不挂客户端 tick 事件。
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
        val camPos = camera.position
        val font = client.font
        // 每帧惰性索引：ENTITY/PLAYER 绑定从 O(实例×实体) 的全表扫降为 O(1)
        var entitiesByUuid: Map<java.util.UUID, Entity>? = null
        var playersByName: Map<String, Entity>? = null

        for (instance in instances) {
            val spec = instance.spec
            var boundEntity: Entity? = null
            val baseX: Double
            val baseY: Double
            val baseZ: Double
            when (instance.bind) {
                ImageBind.PLAYER -> {
                    val index = playersByName ?: level.players()
                        .associateBy { it.name.string }
                        .also { playersByName = it }
                    val player = index[instance.bindPlayer]
                    if (player == null) continue
                    boundEntity = player
                    val p = player.getPosition(partialTick)
                    baseX = p.x; baseY = p.y; baseZ = p.z
                }
                ImageBind.ENTITY -> {
                    val index = entitiesByUuid ?: level.entitiesForRendering()
                        .associateBy { it.uuid }
                        .also { entitiesByUuid = it }
                    val entity = index[instance.bindEntity]
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
            // alpha 低于文字着色器丢弃阈值（0.1）时，Font 会把高位为 0 的颜色强制改回
            // 不透明（0xFC000000 规则），表现为淡出末尾闪回——直接跳过绘制
            if (alpha < 0.1) continue

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

    /**
     * 贴图面片：RenderType.text 走名牌渲染管线（混合 + 顶点色 alpha），FULL_LIGHT 全亮。
     * 贴图每帧按 texturePath 现解析——http 直链首次返回缺失占位、下载完成后自动换上，
     * 不在 spec 里缓存首次解析结果
     */
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
        val texture = resolveTexture(spec.texturePath) ?: return
        val renderType = if (spec.seeThrough) RenderType.textSeeThrough(texture) else RenderType.text(texture)
        val consumer = buffers.getBuffer(renderType)
        val half = (spec.size * scale / 2.0).toFloat()
        val color = ((alpha * 255).toInt() shl 24) or 0x00FFFFFF
        val point = Vector3f()
        vertex(consumer, matrix, point, centerX, centerY, centerZ, right, up, -half, -half, 0.0f, 1.0f, color)
        vertex(consumer, matrix, point, centerX, centerY, centerZ, right, up, half, -half, 1.0f, 1.0f, color)
        vertex(consumer, matrix, point, centerX, centerY, centerZ, right, up, half, half, 1.0f, 0.0f, color)
        vertex(consumer, matrix, point, centerX, centerY, centerZ, right, up, -half, half, 0.0f, 0.0f, color)
    }

    /**
     * 原版字体文字：drawInBatch 复用文字批处理（支持投影/穿墙模式）。
     * 矩阵与原版名牌同构（rotate(朝向) + scale(s, -s, s)）：局部 y 翻转适配字体
     * 向下增长的坐标、z 保持正向让正文落在投影前方（原版名牌同款投影层次）；
     * 字符行高 9px 映射为 size 格，水平按 font.width 居中
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
            .rotate(rotation)
            .scale(textScale, -textScale, textScale)
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

    /** 单顶点：中心 + 基向量张开的偏移，经基矩阵变换后提交（float 重载在三代 VertexConsumer 上都存在）。 */
    private fun vertex(
        consumer: VertexConsumer,
        matrix: Matrix4f,
        point: Vector3f,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        right: Vector3f,
        up: Vector3f,
        rx: Float,
        uy: Float,
        u: Float,
        v: Float,
        color: Int
    ) {
        point.set(
            centerX + right.x * rx + up.x * uy,
            centerY + right.y * rx + up.y * uy,
            centerZ + right.z * rx + up.z * uy
        )
        matrix.transformPosition(point)
        consumer.addVertex(point.x, point.y, point.z).setColor(color).setUv(u, v).setLight(FULL_LIGHT)
    }

    /** 渲染时现解析：http 走 TextureHandler（下载完成后自动换真图），本地路径走动态资源包。 */
    private fun resolveTexture(path: String?): ResourceLocation? {
        if (path == null) return null
        return runCatching {
            if (TextureHandler.isHttpUrl(path)) TextureHandler.getTexture(path)
            else IdentifierBridge.of(Lantern.MOD_ID, path)
        }.getOrElse {
            Lantern.logger.warn("[Lantern] world image: 非法贴图路径 '$path'：${it.message}")
            null
        }
    }
}
