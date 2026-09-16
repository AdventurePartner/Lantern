package org.lantern.costume.geo

import net.minecraft.resources.ResourceLocation
import org.lantern.costume.bone.BoneRotation
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderstate.CostumeRenderData
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import software.bernie.geckolib.animatable.processing.AnimationState
import software.bernie.geckolib.cache.GeckoLibResources
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

class CostumeGeoModel(
    private val wrapper: CostumeModelWrapper,
    /** P1 玩家宿主化：per-UUID 渲染器的归属玩家，hostDriven 时贴图取其真实皮肤 */
    @Volatile private var ownerPlayerId: java.util.UUID? = null
) : GeoModel<CostumeAnimatable>() {

    fun bindPlayer(playerId: java.util.UUID) {
        ownerPlayerId = playerId
    }

    /**
     * hostDriven：返回玩家真实皮肤（几何 UV 即标准 64x64 皮肤布局，
     * 方块人=玩家皮肤人偶）；皮肤未加载时回落 wrapper 静态贴图。
     * 1.21.10 皮肤链：Connection.getPlayerInfo(uuid).getSkin().body().texturePath()
     */
    override fun getTextureResource(renderState: GeoRenderState): ResourceLocation {
        if (wrapper.hostDriven) {
            val playerId = ownerPlayerId ?: return superTexture()
            val skin = net.minecraft.client.Minecraft.getInstance().connection
                ?.getPlayerInfo(playerId)?.skin
            skin?.let { return it.body.texturePath() }
        }
        return superTexture()
    }

    private fun superTexture(): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    /**
     * hostDriven（P1 玩家宿主化）时本模型实例私有的骨骼树。
     * 与 GenericGeoModel 同款深拷贝隔离：GeckoLibResources 缓存中的骨骼树按模型 ID
     * 全局共享，hostDriven 外观按玩家分配渲染器，多玩家同款必须各持一棵骨骼树，
     * 否则后提交玩家的姿势写入会覆盖先提交的（串台）。旧快照路径不拷贝——
     * 单骨快照写入本身就是共享树的历史行为，维持原状
     */
    private var isolatedModel: BakedGeoModel? = null

    /**
     * 克隆骨骼的静态初始姿势（rot/pos/scale 各 3 轴共 9 值），
     * hostDriven 渲染阶段写骨骼时的回落基准，克隆时一次定格
     */
    var initialPose: Map<String, FloatArray> = emptyMap()
        private set

    override fun getModelResource(renderState: GeoRenderState): ResourceLocation =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getAnimationResource(animatable: CostumeAnimatable): ResourceLocation =
        GeckoResourceIds.animation(wrapper.animationLocation)

    override fun getBakedModel(location: ResourceLocation): BakedGeoModel {
        if (!wrapper.hostDriven) return super.getBakedModel(location)
        val shared = GeckoLibResources.getBakedModels()[location]
            ?: throw IllegalArgumentException("[Lantern] Costume model not baked: $location")
        isolatedModel?.let { return it }
        val copies = shared.topLevelBones().map { cloneBone(it, null) }
        val clone = BakedGeoModel(copies, shared.properties())
        getAnimationProcessor().setActiveModel(clone)
        isolatedModel = clone
        initialPose = buildInitialPose(copies)
        return clone
    }

    private fun buildInitialPose(topLevel: List<GeoBone>): Map<String, FloatArray> {
        val out = HashMap<String, FloatArray>()
        captureInitial(topLevel, out)
        return out
    }

    private fun captureInitial(bones: List<GeoBone>, out: MutableMap<String, FloatArray>) {
        for (bone in bones) {
            out[bone.name] = floatArrayOf(
                bone.rotX, bone.rotY, bone.rotZ,
                bone.posX, bone.posY, bone.posZ,
                bone.scaleX, bone.scaleY, bone.scaleZ
            )
            captureInitial(bone.childBones, out)
        }
    }

    private fun cloneBone(source: GeoBone, parent: GeoBone?): GeoBone {
        val copy = GeoBone(
            parent,
            source.name,
            source.mirror,
            source.inflate,
            source.shouldNeverRender(),
            source.reset
        )
        copy.updatePivot(source.pivotX, source.pivotY, source.pivotZ)
        val init = source.initialSnapshot
        if (init != null) {
            copy.updateRotation(init.rotX, init.rotY, init.rotZ)
            copy.updatePosition(init.offsetX, init.offsetY, init.offsetZ)
            copy.updateScale(init.scaleX, init.scaleY, init.scaleZ)
        } else {
            copy.updateRotation(source.rotX, source.rotY, source.rotZ)
        }
        copy.cubes.addAll(source.cubes)
        for (child in source.childBones) {
            copy.childBones.add(cloneBone(child, copy))
        }
        return copy
    }

    override fun setCustomAnimations(animationState: AnimationState<CostumeAnimatable>) {
        if (wrapper.hostDriven) return
        if (!wrapper.boneSyncEnabled) return
        val snapshot = requireNotNull(animationState.getData(CostumeRenderData.PLAYER_POSE))
        val mapping = wrapper.boneMapping
        applyRotation(mapping.head, snapshot.head)
        applyRotation(mapping.body, snapshot.body)
        applyRotation(mapping.leftArm, snapshot.leftArm)
        applyRotation(mapping.rightArm, snapshot.rightArm)
        applyRotation(mapping.leftLeg, snapshot.leftLeg)
        applyRotation(mapping.rightLeg, snapshot.rightLeg)
    }

    private fun applyRotation(name: String, rotation: BoneRotation) {
        animationProcessor.getBone(name)?.updateRotation(rotation.x, rotation.y, rotation.z)
    }

    /**
     * hostDriven 时禁用 GeckoLib 的动画处理（与 GenericGeoModel 同款）：
     * 骨骼唯一写入方是 AnimationHost（见 CostumeRenderer 的两阶段驱动）
     */
    override fun handleAnimations(state: AnimationState<CostumeAnimatable>) {
        if (wrapper.hostDriven) return
        super.handleAnimations(state)
    }

    override fun prepareForRenderPass(
        animatable: CostumeAnimatable,
        renderState: GeoRenderState,
        partialTick: Float
    ) {
        if (wrapper.hostDriven) return
        super.prepareForRenderPass(animatable, renderState, partialTick)
    }
}
