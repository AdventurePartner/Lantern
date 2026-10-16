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
 * 共享 GenericGeoRenderer 的 1.20.1 版：applyRotations 少 nativeScale 参数、getPackedOverlay
 * 有两参重载；名牌 offset-y 由 EntityRenderer1201Mixin 叠加。其余逻辑与共享版一致
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
        private val engagedKeys = ConcurrentHashMap.newKeySet<String>()
    }

    private val genericModel = geoModel as GenericGeoModel<T>

    private var isRenderingNameTag = false

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

    private fun resolveBindTransform(entity: Entity, partialTick: Float): BindTransform? {
        val bind = BindStore.get(entity.uuid) ?: return null
        val host = EntityLookupBridge.byUuid(bind.host) ?: return null
        val hostYaw = hostBodyYaw(host, partialTick)

        var offsetX = bind.offsetX
        val offsetY = bind.offsetY
        var offsetZ = bind.offsetZ
        if (bind.rotate) {
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

    override fun shouldRender(entity: Entity, frustum: Frustum, camX: Double, camY: Double, camZ: Double): Boolean {
        val bind = BindStore.get(entity.uuid) ?: return super.shouldRender(entity, frustum, camX, camY, camZ)
        if (bind.visible) return true
        val host = EntityLookupBridge.byUuid(bind.host) ?: return false
        val slack = 1.0 + Math.abs(bind.offsetX) + Math.abs(bind.offsetY) + Math.abs(bind.offsetZ)
        return frustum.isVisible(host.boundingBox.inflate(slack))
    }

    override fun applyRotations(
        animatable: GenericReplacedEntity<T>,
        poseStack: PoseStack,
        ageInTicks: Float,
        rotationYaw: Float,
        partialTick: Float
    ) {
        val hostYaw = bindTransform?.hostBodyYaw
        if (hostYaw != null) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - hostYaw))
            return
        }
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick)
    }

    override fun getDeathMaxRotation(animatable: GenericReplacedEntity<T>): Float =
        if (wrapper.animationStates.death != null) 0f else super.getDeathMaxRotation(animatable)

    override fun getPackedOverlay(animatable: GenericReplacedEntity<T>, u: Float): Int =
        if (suppressDeathOverlay()) OverlayTexture.NO_OVERLAY else super.getPackedOverlay(animatable, u)

    override fun getPackedOverlay(animatable: GenericReplacedEntity<T>, u: Float, partialTick: Float): Int =
        if (suppressDeathOverlay()) OverlayTexture.NO_OVERLAY else super.getPackedOverlay(animatable, u, partialTick)

    private fun suppressDeathOverlay(): Boolean {
        val entity = currentEntity
        return wrapper.animationStates.death != null && entity is LivingEntity && entity.isDeadOrDying
    }

    override fun shouldShowName(arg: Entity): Boolean {
        if (wrapper.hiddenName) {
            return false
        }
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
        packedLight: Int
    ) {
        if (wrapper.hiddenName) {
            return
        }
        super.renderNameTag(entity, component, poseStack, bufferSource, packedLight)
    }

    override fun getTextureLocation(entity: Entity): ResourceLocation {
        val url = wrapper.textureUrl
        return if (url != null) TextureHandler.getTexture(url) else wrapper.textureLocation
    }
}
