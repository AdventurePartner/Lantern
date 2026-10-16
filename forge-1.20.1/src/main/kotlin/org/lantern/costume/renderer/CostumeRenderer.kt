package org.lantern.costume.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import org.lantern.animation.AnimationHost
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.geo.CostumeGeoModel
import org.lantern.costume.wrapper.CostumeModelWrapper
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoObjectRenderer

/** 共享 CostumeRenderer 的 1.20.1 版：GeckoLib 4.x 旧签名（颜色拆成 r/g/b/a、render 无 partialTick） */
class CostumeRenderer(
    private val wrapper: CostumeModelWrapper,
    val animatable: CostumeAnimatable
) : GeoObjectRenderer<CostumeAnimatable>(CostumeGeoModel(wrapper)) {

    val costumeModel: CostumeGeoModel = geoModel as CostumeGeoModel

    var currentBoneSnapshot: PlayerBoneSnapshot? = null

    private val itemLayer by lazy { CostumeItemLayer(this) }

    private val boneSyncMap: Map<String, (GeoBone, PlayerBoneSnapshot) -> Unit> = buildBoneSyncMap()

    private fun buildBoneSyncMap(): Map<String, (GeoBone, PlayerBoneSnapshot) -> Unit> {
        if (!wrapper.boneSyncEnabled || wrapper.hostDriven) return emptyMap()
        val mapping = wrapper.boneMapping
        return buildMap {
            put(mapping.head) { bone, snap -> bone.rotX = snap.head.rotX; bone.rotY = snap.head.rotY; bone.rotZ = snap.head.rotZ }
            put(mapping.body) { bone, snap -> bone.rotX = snap.body.rotX; bone.rotY = snap.body.rotY; bone.rotZ = snap.body.rotZ }
            put(mapping.leftArm) { bone, snap -> bone.rotX = snap.leftArm.rotX; bone.rotY = snap.leftArm.rotY; bone.rotZ = snap.leftArm.rotZ }
            put(mapping.rightArm) { bone, snap -> bone.rotX = snap.rightArm.rotX; bone.rotY = snap.rightArm.rotY; bone.rotZ = snap.rightArm.rotZ }
            put(mapping.leftLeg) { bone, snap -> bone.rotX = snap.leftLeg.rotX; bone.rotY = snap.leftLeg.rotY; bone.rotZ = snap.leftLeg.rotZ }
            put(mapping.rightLeg) { bone, snap -> bone.rotX = snap.rightLeg.rotX; bone.rotY = snap.rightLeg.rotY; bone.rotZ = snap.rightLeg.rotZ }
        }
    }

    init {
        scaleWidth = wrapper.scale
        scaleHeight = wrapper.scale
    }

    override fun getInstanceId(animatable: CostumeAnimatable): Long {
        return this.animatable.currentEntity?.id?.toLong() ?: animatable.hashCode().toLong()
    }

    override fun getRenderType(
        animatable: CostumeAnimatable,
        texture: ResourceLocation,
        bufferSource: MultiBufferSource?,
        partialTick: Float
    ): RenderType {
        return RenderType.entityCutoutNoCull(texture)
    }

    override fun preRender(
        poseStack: PoseStack,
        animatable: CostumeAnimatable,
        model: BakedGeoModel,
        bufferSource: MultiBufferSource?,
        buffer: VertexConsumer?,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        objectRenderTranslations = Matrix4f(poseStack.last().pose())
        scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay)
        poseStack.translate(wrapper.offsetX.toDouble(), wrapper.offsetY.toDouble(), wrapper.offsetZ.toDouble())
    }

    override fun renderRecursively(
        poseStack: PoseStack,
        animatable: CostumeAnimatable,
        bone: GeoBone,
        renderType: RenderType,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        if (wrapper.boneSyncEnabled) {
            val snapshot = currentBoneSnapshot
            if (snapshot != null) {
                boneSyncMap[bone.name]?.invoke(bone, snapshot)
            }
        }
        super.renderRecursively(
            poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
            partialTick, packedLight, packedOverlay, red, green, blue, alpha
        )
    }

    override fun postRender(
        poseStack: PoseStack,
        animatable: CostumeAnimatable,
        model: BakedGeoModel,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer?,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        if (wrapper.hostDriven && !isReRender) {
            (animatable.currentEntity as? LivingEntity)?.let { itemLayer.renderItems(poseStack, it, bufferSource, packedLight) }
        }
        super.postRender(
            poseStack, animatable, model, bufferSource, buffer, isReRender,
            partialTick, packedLight, packedOverlay, red, green, blue, alpha
        )
    }

    fun prepareHostPose(entity: Entity) {
        animatable.currentEntity = entity
        costumeModel.getBakedModel(wrapper.modelLocation)
        AnimationHost.applyPose(
            costumeModel.animationProcessor,
            AnimationHost.drivePose(entity, wrapper.animationLocation, wrapper.animationStates),
            costumeModel.initialPose
        )
    }

    fun render(
        entity: Entity,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        partialTick: Float
    ) {
        animatable.currentEntity = entity
        animatable.currentAnimationStates = wrapper.animationStates
        if (wrapper.hostDriven) prepareHostPose(entity)
        render(poseStack, animatable, bufferSource, null, null, packedLight)
    }
}
