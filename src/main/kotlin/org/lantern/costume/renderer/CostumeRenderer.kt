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

class CostumeRenderer(
    private val wrapper: CostumeModelWrapper,
    val animatable: CostumeAnimatable
) : GeoObjectRenderer<CostumeAnimatable>(CostumeGeoModel(wrapper)) {

    val costumeModel: CostumeGeoModel = geoModel as CostumeGeoModel

    var currentBoneSnapshot: PlayerBoneSnapshot? = null

    /** hostDriven 外观的手持物挂到模型的 rightItem / leftItem 骨上 */
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
        colour: Int
    ) {
        objectRenderTranslations = Matrix4f(poseStack.last().pose())
        scaleModelForRender(scaleWidth, scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay)
        // Apply costume offset (no default 0.5/0.51/0.5 block-center offset)
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
        colour: Int
    ) {
        if (wrapper.boneSyncEnabled) {
            val snapshot = currentBoneSnapshot
            if (snapshot != null) {
                boneSyncMap[bone.name]?.invoke(bone, snapshot)
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour)
    }

    /** postRender 时 poseStack 处于模型根空间（preRender 的缩放与偏移之后），手持物从这里沿骨链挂上去 */
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
        colour: Int
    ) {
        if (wrapper.hostDriven && !isReRender) {
            (animatable.currentEntity as? LivingEntity)?.let { itemLayer.renderItems(poseStack, it, bufferSource, packedLight) }
        }
        super.postRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour)
    }

    /**
     * hostDriven：算出本帧姿势写进本渲染器的克隆骨骼。在玩家渲染一开始就调用
     * （原版 PlayerModel 部件与披风等附件要读到本帧骨骼），渲染时再调一次是幂等的——
     * AnimationHost 按帧判重，写骨骼只是覆盖同一组值
     */
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
        render(poseStack, animatable, bufferSource, null, null, packedLight, partialTick)
    }
}
