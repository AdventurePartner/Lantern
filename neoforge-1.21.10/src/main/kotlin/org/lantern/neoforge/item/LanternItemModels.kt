package org.lantern.neoforge.item

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.SubmitNodeCollector
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

        originals.forEach { (location, original) ->
            itemModels[location] = LanternItemModel(original, iconModels, blockItemModel, costumeItemModel)
        }
    }
}

private class LanternItemModel(
    private val original: ItemModel,
    private val iconModels: Map<Int, ItemModel>,
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
        val selected = when {
            displayContext.isHandContext() && getCostume(stack) != null -> costumeItemModel
            stack.`is`(Items.BARREL) && getBlockWrapper(stack) != null -> blockItemModel
            else -> customModelData(stack)?.let(iconModels::get) ?: original
        }
        selected.update(renderState, stack, resolver, displayContext, level, owner, seed)
    }
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
    val value = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.getFloat(0) ?: return null
    val customModelData = value.toInt()
    return customModelData.takeIf { it.toFloat() == value }
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
