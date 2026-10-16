package org.lantern.model.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import org.lantern.Lantern
import org.lantern.animation.AnimationHost
import org.lantern.core.bind.BindStore
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.entity.GenericReplacedEntity
import org.lantern.model.geo.GenericGeoModel
import org.lantern.model.wrapper.CustomModelWrapper
import org.lantern.platform.EntityLookupBridge
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer

/**
 * Lantern 实体模型渲染器（Fabric 1.21.1 / Forge 1.21.1；1.20.1 与 1.21.10 各有副本）。
 *
 * 每个实体一个实例（RendererHandler 按 UUID 分配），骨骼树由 GenericGeoModel 深拷贝隔离。
 * render 里先由 AnimationHost 计算本帧姿势并写入骨骼，再交给 GeckoLib 绘制
 */
class GenericGeoRenderer<T : Entity>(
    entityType: EntityType<T>,
    val rendererKey: String,
    val wrapper: CustomModelWrapper
) : GeoReplacedEntityRenderer<Entity, GenericReplacedEntity<T>>(
    CycleHandler.context,
    GenericGeoModel(wrapper),
    GenericReplacedEntity(entityType)
) {
    companion object {
        private val warnedModels = ConcurrentHashMap.newKeySet<ResourceLocation>()

        /** 首次渲染回执：区分「实体没进渲染链（名字没匹配）」与「进了但播控没到」 */
        private val engagedKeys = ConcurrentHashMap.newKeySet<String>()
    }

    private val genericModel = geoModel as GenericGeoModel<T>

    // 用于防止重复渲染名字（渲染在主线程执行，无需 @Volatile）
    private var isRenderingNameTag = false

    /** 本次 render 的载体绑定重定向；applyRotations 在 super.render 内读取 */
    private var bindTransform: BindTransform? = null

    private class BindTransform(val dx: Double, val dy: Double, val dz: Double, val hostBodyYaw: Float?)

    init {
        scaleHeight = wrapper.scale
        scaleWidth = wrapper.scale
    }

    override fun render(
        entity: Entity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        isRenderingNameTag = false

        // GeckoLib 的 getBakedModel() 在缓存中找不到模型时直接抛异常，且发生在 pushPose 之后，
        // 会泄漏矩阵栈；进入渲染前先检查缓存，模型不可用时直接跳过
        if (GeckoLibCache.getBakedModels()[wrapper.modelLocation] == null) {
            if (warnedModels.add(wrapper.modelLocation)) {
                Lantern.logger.warn(
                    "[Lantern] 实体模型 {}（{}）不在 GeckoLib 缓存中，跳过渲染；" +
                        "请确认文件位于客户端资源包 assets/lantern/geo/ 且 entityModels.yml 路径以 geo/ 开头",
                    rendererKey, wrapper.modelLocation
                )
            }
            return
        }
        if (engagedKeys.add(rendererKey)) {
            Lantern.logger.info("[Lantern] 实体模型接管渲染: 条目='{}' 首个实体={}", rendererKey, entity.uuid)
        }

        // 克隆骨骼树要先建好才能写姿势；GeckoLib 的 handleAnimations 已置空，不会再改骨骼
        genericModel.getBakedModel(wrapper.modelLocation)
        AnimationHost.applyPose(
            genericModel.animationProcessor,
            AnimationHost.drivePose(entity, wrapper),
            genericModel.initialPose
        )

        val bind = resolveBindTransform(entity, partialTick)
        bindTransform = bind
        try {
            if (bind == null) {
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
                return
            }
            // 载体绑定：服务端坐标不动，纯渲染层把模型平移到宿主位置。push/pop 包住，
            // 调用方还要用同一层矩阵画影子与碰撞箱
            poseStack.pushPose()
            try {
                poseStack.translate(bind.dx, bind.dy, bind.dz)
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
            } finally {
                poseStack.popPose()
            }
        } finally {
            bindTransform = null
        }
    }

    /**
     * 载体绑定的渲染重定向量。位置一律取插值位置，与原版渲染取的同一口径——混用 tick
     * 位置和插值位置会让载体相对宿主每帧抖一下。宿主在本客户端不存在时返回 null，按载体
     * 自身位置渲染（visible=false 的条目在 shouldRender 里已被跳过）
     */
    private fun resolveBindTransform(entity: Entity, partialTick: Float): BindTransform? {
        val bind = BindStore.get(entity.uuid) ?: return null
        val host = EntityLookupBridge.byUuid(bind.host) ?: return null
        val hostYaw = hostBodyYaw(host, partialTick)

        var offsetX = bind.offsetX
        val offsetY = bind.offsetY
        var offsetZ = bind.offsetZ
        if (bind.rotate) {
            // 局部坐标 -> 世界坐标。MC 的 yaw 0 面朝 +Z：前向 = (-sin, cos)，右向 = (-cos, -sin)。
            // 约定 x=右 y=上 z=前
            val yawRad = Math.toRadians(hostYaw.toDouble())
            val forwardX = -Math.sin(yawRad)
            val forwardZ = Math.cos(yawRad)
            val rightX = -Math.cos(yawRad)
            val rightZ = -Math.sin(yawRad)
            val localX = offsetX
            val localZ = offsetZ
            offsetX = localX * rightX + localZ * forwardX
            offsetZ = localX * rightZ + localZ * forwardZ
        }

        return BindTransform(
            Mth.lerp(partialTick.toDouble(), host.xOld, host.x) + offsetX - Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
            Mth.lerp(partialTick.toDouble(), host.yOld, host.y) + offsetY - Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
            Mth.lerp(partialTick.toDouble(), host.zOld, host.z) + offsetZ - Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z),
            if (bind.rotate) hostYaw else null
        )
    }

    private fun hostBodyYaw(host: Entity, partialTick: Float): Float =
        if (host is LivingEntity) Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot)
        else Mth.rotLerp(partialTick, host.yRotO, host.yRot)

    /**
     * 视锥剔除按宿主判定：载体的服务端坐标停在召唤点不动，画出来的却是宿主的位置。
     * visible=true 直接跳过剔除；宿主不存在时 visible=false 不渲染，免得弹回召唤点
     */
    override fun shouldRender(entity: Entity, frustum: Frustum, camX: Double, camY: Double, camZ: Double): Boolean {
        val bind = BindStore.get(entity.uuid) ?: return super.shouldRender(entity, frustum, camX, camY, camZ)
        if (bind.visible) return true
        val host = EntityLookupBridge.byUuid(bind.host) ?: return false
        val slack = 1.0 + Math.abs(bind.offsetX) + Math.abs(bind.offsetY) + Math.abs(bind.offsetZ)
        return frustum.isVisible(host.boundingBox.inflate(slack))
    }

    /**
     * rotate=true 时用宿主的身体朝向直接替换载体朝向：GeckoLib 之后的死亡倒地、睡眠、
     * 旋风斩这些姿态分支对挂件载体全都不适用，一并跳掉
     */
    override fun applyRotations(
        animatable: GenericReplacedEntity<T>,
        poseStack: PoseStack,
        ageInTicks: Float,
        rotationYaw: Float,
        partialTick: Float,
        nativeScale: Float
    ) {
        val hostYaw = bindTransform?.hostBodyYaw
        if (hostYaw != null) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - hostYaw))
            return
        }
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale)
    }

    /** 配置了死亡动画就由动画表现倒下，不再叠原版的侧翻 */
    override fun getDeathMaxRotation(animatable: GenericReplacedEntity<T>): Float =
        if (wrapper.animationStates.death != null) 0f else super.getDeathMaxRotation(animatable)

    /** 死亡动画持有末帧期间不打死亡红闪；存活期的受击红闪保留 */
    override fun getPackedOverlay(animatable: GenericReplacedEntity<T>, u: Float, partialTick: Float): Int {
        val entity = currentEntity
        if (wrapper.animationStates.death != null && entity is LivingEntity && entity.isDeadOrDying) {
            return OverlayTexture.NO_OVERLAY
        }
        return super.getPackedOverlay(animatable, u, partialTick)
    }

    override fun shouldShowName(arg: Entity): Boolean {
        if (wrapper.hiddenName) {
            return false
        }
        // 防止重复渲染：如果已经在渲染中，跳过
        if (isRenderingNameTag) {
            return false
        }
        isRenderingNameTag = true
        return super.shouldShowName(arg)
    }

    override fun renderNameTag(
        entity: Entity,
        component: Component,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        partialTick: Float
    ) {
        if (wrapper.hiddenName) {
            return
        }

        poseStack.pushPose()
        try {
            if (wrapper.nameTagOffsetY != 0.0F) {
                poseStack.translate(0.0, wrapper.nameTagOffsetY.toDouble(), 0.0)
            }
            super.renderNameTag(entity, component, poseStack, bufferSource, packedLight, partialTick)
        } finally {
            poseStack.popPose()
        }
    }

    override fun getTextureLocation(entity: Entity): ResourceLocation {
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }
}
