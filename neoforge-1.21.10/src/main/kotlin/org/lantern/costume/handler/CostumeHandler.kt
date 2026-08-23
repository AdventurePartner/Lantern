package org.lantern.costume.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderer.CostumeRenderer
import org.lantern.costume.renderstate.CostumeRenderContext
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.model.GeckoResourceIds
import software.bernie.geckolib.cache.GeckoLibResources
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeHandler {
    private val definitions = ConcurrentHashMap<String, CostumeModelWrapper>()
    private val playerCostumes = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, String>>()
    private val renderers = ConcurrentHashMap<String, RendererEntry>()
    private val warnedModels = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun reload() {
        definitions.clear()
        playerCostumes.clear()
        renderers.clear()
        warnedModels.clear()
    }

    fun replaceDefinitions(newDefinitions: Map<String, CostumeModelWrapper>) {
        definitions.clear()
        definitions.putAll(newDefinitions)
        renderers.clear()
        warnedModels.clear()
        playerCostumes.values.forEach { slots ->
            slots.entries.removeIf { (_, costumeId) -> !definitions.containsKey(costumeId) }
        }
    }

    fun getCostume(costumeId: String): CostumeModelWrapper? = definitions[costumeId]

    fun refreshRenderers() {
        renderers.clear()
        warnedModels.clear()
    }

    fun hasAny(playerUUID: UUID): Boolean = playerCostumes[playerUUID]?.isNotEmpty() == true

    fun getAssignedCostumes(playerUUID: UUID): Map<CostumeSlot, CostumeModelWrapper> =
        playerCostumes[playerUUID]
            ?.mapNotNull { (slot, costumeId) -> definitions[costumeId]?.let { slot to it } }
            ?.toMap()
            ?: emptyMap()

    fun assignCostume(playerUUID: UUID, costumeId: String) {
        val wrapper = definitions[costumeId] ?: return
        playerCostumes.computeIfAbsent(playerUUID) { ConcurrentHashMap() }[wrapper.slot] = costumeId
    }

    fun removeCostume(playerUUID: UUID, slot: CostumeSlot? = null) {
        if (slot == null) {
            playerCostumes.remove(playerUUID)
        } else {
            playerCostumes[playerUUID]?.remove(slot)
        }
    }

    fun removePlayer(playerUUID: UUID) {
        playerCostumes.remove(playerUUID)
    }

    fun submitForPlayer(
        context: CostumeRenderContext,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        cameraState: CameraRenderState,
        packedLight: Int,
        partialTick: Float
    ) {
        val assigned = playerCostumes[context.playerId] ?: return
        assigned.values.forEach { costumeId ->
            val wrapper = definitions[costumeId] ?: return@forEach
            val modelId = GeckoResourceIds.model(wrapper.modelLocation)
            if (!GeckoLibResources.getBakedModels().containsKey(modelId)) {
                if (warnedModels.add(modelId)) {
                    Lantern.logger.warn("[Lantern] Costume model not yet cached, deferring render: {}", modelId)
                }
                return@forEach
            }
            val entry = renderers.computeIfAbsent(costumeId) {
                val animatable = CostumeAnimatable(wrapper.animationStates)
                RendererEntry(animatable, CostumeRenderer(wrapper, animatable))
            }
            entry.renderer.submit(
                poseStack,
                entry.animatable,
                context,
                submitNodes,
                cameraState,
                packedLight,
                partialTick,
                null
            )
        }
    }

    private data class RendererEntry(
        val animatable: CostumeAnimatable,
        val renderer: CostumeRenderer
    )
}
