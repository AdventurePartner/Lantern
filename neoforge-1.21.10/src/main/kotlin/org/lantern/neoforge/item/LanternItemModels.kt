package org.lantern.neoforge.item

import com.google.common.collect.ImmutableMap
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.item.ModelRenderProperties
import net.minecraft.client.renderer.item.SpecialModelWrapper
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.client.event.ModelEvent
import org.joml.Vector3f
import org.lantern.Lantern
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.icon.IconMesh
import org.lantern.internal.icon.IconVertexEmitter
import org.lantern.internal.icon.ItemIconMeshes
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.wrapper.BlockModelWrapper
import org.lantern.neoforge.block.TrackedBarrelModel
import org.lantern.platform.IdentifierBridge
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.cache.GeckoLibResources
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.renderer.base.GeoRenderState
import software.bernie.geckolib.util.ClientUtil
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.concurrent.ConcurrentHashMap

object LanternItemModels {
    @JvmStatic
    fun modifyBakingResult(event: ModelEvent.ModifyBakingResult) {
        val blockStateModels = event.bakingResult.blockStateModels()
        net.minecraft.world.level.block.Blocks.BARREL.stateDefinition.possibleStates.forEach { state ->
            val current = blockStateModels[state] ?: return@forEach
            if (current !is TrackedBarrelModel) {
                blockStateModels[state] = TrackedBarrelModel(current)
            }
        }

        val itemModels = event.bakingResult.itemStackModels()
        val originals = HashMap(itemModels)
        val iconModels = ResourceHandler.getItemIcons().mapNotNull { (customModelData, identifier) ->
            originals[IdentifierBridge.of(Lantern.MOD_ID, identifier)]?.let { customModelData to it }
        }.toMap()
        val particle = event.textureGetter.apply(ResourceLocation.withDefaultNamespace("block/barrel_side"))
        val properties = ModelRenderProperties(true, particle, ItemTransforms.NO_TRANSFORMS)
        val blockItemModel: ItemModel = SpecialModelWrapper(BlockItemSpecialRenderer(), properties)
        val costumeItemModel: ItemModel = SpecialModelWrapper(CostumeItemSpecialRenderer(), properties)
        val iconRenderer = IconSpecialRenderer()
        val runtimeIcons = RuntimeIconModels(
            generated = SpecialModelWrapper(iconRenderer, ModelRenderProperties(false, particle, IconTransforms.GENERATED)),
            handheld = SpecialModelWrapper(iconRenderer, ModelRenderProperties(false, particle, IconTransforms.HANDHELD))
        )

        originals.forEach { (location, original) ->
            itemModels[location] = LanternItemModel(original, iconModels, runtimeIcons, blockItemModel, costumeItemModel)
        }
    }
}

private class RuntimeIconModels(val generated: ItemModel, val handheld: ItemModel)

private class LanternItemModel(
    private val original: ItemModel,
    private val iconModels: Map<Int, ItemModel>,
    private val runtimeIcons: RuntimeIconModels,
    private val blockItemModel: ItemModel,
    private val costumeItemModel: ItemModel
) : ItemModel {
    override fun update(
        renderState: ItemStackRenderState,
        stack: ItemStack,
        resolver: ItemModelResolver,
        displayContext: ItemDisplayContext,
        level: ClientLevel?,
        owner: ItemOwner?,
        seed: Int
    ) {
        // 外观物品、木桶方块模型还在后台加载时画原版物品
        val selected = when {
            displayContext.isHandContext() && getCostume(stack)?.let(CostumeHandler::isItemReady) == true -> costumeItemModel
            stack.`is`(Items.BARREL) && getBlockWrapper(stack)?.isReady() == true -> blockItemModel
            else -> customModelData(stack)?.let(::iconModel) ?: original
        }
        selected.update(renderState, stack, resolver, displayContext, level, owner, seed)
    }

    /** 运行时图标优先（不依赖模型烘焙）；网格还没生成时退到烘焙时的快照，再没有就是原版物品 */
    private fun iconModel(customModelData: Int): ItemModel? {
        val mesh = ItemIconMeshes.get(customModelData) ?: return iconModels[customModelData]
        return if (mesh.handheld) runtimeIcons.handheld else runtimeIcons.generated
    }
}

/**
 * 运行时图标：网格由 ItemIconMeshes 在后台按贴图生成，贴图单独绑定、不进方块图集。
 * 图层已应用 display 变换与 -0.5 平移，这里直接按 0..1 模型空间提交；
 * 参数（网格实例）进入 GUI 物品缓存的身份，网格重建后缓存自然失效
 */
private class IconSpecialRenderer : SpecialModelRenderer<IconMesh> {
    override fun submit(
        argument: IconMesh?,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        val mesh = argument ?: return
        submitNodes.submitCustomGeometry(poseStack, RenderType.itemEntityTranslucentCull(mesh.texture)) { pose, consumer ->
            IconVertexEmitter.emit(pose, consumer, mesh, packedLight, packedOverlay)
        }
    }

    override fun getExtents(extents: MutableSet<Vector3f>) {
        extents.add(Vector3f(0.0f, 0.0f, 7.5f / 16.0f))
        extents.add(Vector3f(1.0f, 1.0f, 8.5f / 16.0f))
    }

    override fun extractArgument(stack: ItemStack): IconMesh? = customModelData(stack)?.let(ItemIconMeshes::get)
}

/** 原版 models/item/generated.json 与 handheld.json 的 display 表；平移按 json 的像素值换算成方块单位 */
private object IconTransforms {
    val GENERATED: ItemTransforms = build(handheld = false)
    val HANDHELD: ItemTransforms = build(handheld = true)

    private fun build(handheld: Boolean): ItemTransforms {
        val thirdPersonRight = if (handheld) transform(0f, -90f, 55f, 0f, 4f, 0.5f, 0.85f)
            else transform(0f, 0f, 0f, 0f, 3f, 1f, 0.55f)
        val thirdPersonLeft = if (handheld) transform(0f, 90f, -55f, 0f, 4f, 0.5f, 0.85f) else thirdPersonRight
        val firstPersonRight = transform(0f, -90f, 25f, 1.13f, 3.2f, 1.13f, 0.68f)
        val firstPersonLeft = if (handheld) transform(0f, 90f, -25f, 1.13f, 3.2f, 1.13f, 0.68f) else firstPersonRight
        return ItemTransforms(
            thirdPersonLeft,
            thirdPersonRight,
            firstPersonLeft,
            firstPersonRight,
            transform(0f, 180f, 0f, 0f, 13f, 7f, 1f),
            ItemTransform.NO_TRANSFORM,
            transform(0f, 0f, 0f, 0f, 2f, 0f, 0.5f),
            transform(0f, 180f, 0f, 0f, 0f, 0f, 1f),
            ItemTransform.NO_TRANSFORM,
            ImmutableMap.of()
        )
    }

    private fun transform(
        rotationX: Float, rotationY: Float, rotationZ: Float,
        translationX: Float, translationY: Float, translationZ: Float,
        scale: Float
    ): ItemTransform = ItemTransform(
        Vector3f(rotationX, rotationY, rotationZ),
        Vector3f(translationX / 16f, translationY / 16f, translationZ / 16f),
        Vector3f(scale, scale, scale)
    )
}

private data class BlockItemRenderData(
    val wrapper: BlockModelWrapper,
    val instanceId: Long
)

private class BlockItemSpecialRenderer : SpecialModelRenderer<BlockItemRenderData> {
    override fun submit(
        argument: BlockItemRenderData?,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        argument ?: return
        val wrapper = argument.wrapper
        poseStack.pushPose()
        try {
            poseStack.translate(wrapper.itemOffsetX, wrapper.itemOffsetY, wrapper.itemOffsetZ)
            if (displayContext == ItemDisplayContext.GUI) {
                poseStack.mulPose(Axis.XP.rotationDegrees(30.0f))
                poseStack.mulPose(Axis.YP.rotationDegrees(225.0f))
            }
            val contextScale = if (displayContext.isHandContext()) 0.35f else 0.625f
            val scale = wrapper.scale * contextScale
            poseStack.scale(scale, scale, scale)
            poseStack.translate(-0.5, -0.5, -0.5)

            val minecraft = Minecraft.getInstance()
            BlockRendererHandler.submit(
                wrapper,
                argument.instanceId,
                poseStack,
                submitNodes,
                minecraft.gameRenderer.levelRenderState.cameraRenderState,
                packedLight,
                minecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
            )
        } finally {
            poseStack.popPose()
        }
    }

    override fun getExtents(extents: MutableSet<Vector3f>) {
        extents.add(Vector3f(0.0f, 0.0f, 0.0f))
        extents.add(Vector3f(1.0f, 1.0f, 1.0f))
    }

    override fun extractArgument(stack: ItemStack): BlockItemRenderData? =
        getBlockWrapper(stack)?.let { BlockItemRenderData(it, System.identityHashCode(stack).toLong()) }
}

private data class CostumeItemRenderData(
    val wrapper: CostumeModelWrapper,
    val instanceId: Long
)

private class CostumeItemSpecialRenderer : SpecialModelRenderer<CostumeItemRenderData> {
    private val renderers = ConcurrentHashMap<CostumeModelWrapper, CostumeRendererEntry>()
    private val warnedModels = ConcurrentHashMap.newKeySet<ResourceLocation>()

    override fun submit(
        argument: CostumeItemRenderData?,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        argument ?: return
        val modelId = GeckoResourceIds.model(argument.wrapper.modelLocation)
        if (!GeckoLibResources.getBakedModels().containsKey(modelId)) {
            if (warnedModels.add(modelId)) {
                Lantern.logger.warn("[Lantern] Costume item model not yet cached, deferring render: {}", modelId)
            }
            return
        }
        val entry = renderers.computeIfAbsent(argument.wrapper) {
            val animatable = CostumeItemAnimatable()
            CostumeRendererEntry(animatable, CostumeItemGeoRenderer(argument.wrapper))
        }
        val minecraft = Minecraft.getInstance()
        entry.renderer.submit(
            poseStack,
            entry.animatable,
            argument.instanceId,
            submitNodes,
            minecraft.gameRenderer.levelRenderState.cameraRenderState,
            packedLight,
            minecraft.deltaTracker.getGameTimeDeltaPartialTick(true),
            null
        )
    }

    override fun getExtents(extents: MutableSet<Vector3f>) {
        extents.add(Vector3f(-0.5f, -0.5f, -0.5f))
        extents.add(Vector3f(0.5f, 0.5f, 0.5f))
    }

    override fun extractArgument(stack: ItemStack): CostumeItemRenderData? =
        getCostume(stack)?.let { CostumeItemRenderData(it, System.identityHashCode(stack).toLong()) }
}

private class CostumeItemAnimatable : GeoAnimatable {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) = Unit

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(obj: Any?): Double = ClientUtil.getCurrentTick()
}

private class CostumeItemGeoModel(
    private val wrapper: CostumeModelWrapper
) : GeoModel<CostumeItemAnimatable>() {
    override fun getModelResource(renderState: GeoRenderState): ResourceLocation =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getTextureResource(renderState: GeoRenderState): ResourceLocation =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getAnimationResource(animatable: CostumeItemAnimatable): ResourceLocation =
        GeckoResourceIds.animation(wrapper.animationLocation)
}

private class CostumeItemGeoRenderer(wrapper: CostumeModelWrapper) :
    GeoObjectRenderer<CostumeItemAnimatable, Long, GeoRenderState>(CostumeItemGeoModel(wrapper)) {
    init {
        scaleWidth = wrapper.scale * 0.5f
        scaleHeight = wrapper.scale * 0.5f
    }

    override fun getInstanceId(animatable: CostumeItemAnimatable, relatedObject: Long): Long = relatedObject

    override fun adjustRenderPose(
        renderState: GeoRenderState,
        poseStack: PoseStack,
        model: BakedGeoModel,
        cameraState: CameraRenderState
    ) = Unit
}

private data class CostumeRendererEntry(
    val animatable: CostumeItemAnimatable,
    val renderer: CostumeItemGeoRenderer
)

private fun customModelData(stack: ItemStack): Int? {
    val modern = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.getFloat(0)
    if (modern != null) {
        val customModelData = modern.toInt()
        return customModelData.takeIf { it.toFloat() == modern }
    }
    // 旧插件把整数写进 custom_data 的 CustomModelData 键，与共享 ItemRendererMixin 同样回退
    return stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt("CustomModelData")?.orElse(null)
}

private fun getBlockWrapper(stack: ItemStack): BlockModelWrapper? {
    if (!stack.`is`(Items.BARREL)) return null
    return customModelData(stack)?.let(BlockRendererHandler::getWrapperByCustomModelData)
}

private fun getCostume(stack: ItemStack): CostumeModelWrapper? {
    val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
    val modelId = customData.copyTag().getStringOr("LanternModelId", "")
    if (modelId.isBlank()) return null
    return CostumeHandler.getCostume(modelId)
}

private fun ItemDisplayContext.isHandContext(): Boolean =
    firstPerson() || this == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ||
        this == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
