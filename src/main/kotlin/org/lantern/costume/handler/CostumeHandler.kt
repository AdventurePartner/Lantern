package org.lantern.costume.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.Lantern
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderer.CostumeItemRenderer
import org.lantern.costume.renderer.CostumeRenderer
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.mixin.accessor.PoseStackAccessor
import software.bernie.geckolib.cache.GeckoLibCache
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeHandler {
    private val costumeDefinitions = ConcurrentHashMap<String, CostumeModelWrapper>()

    // UUID -> (CostumeSlot -> costumeId)
    private val playerCostumes = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, String>>()

    // UUID -> (CostumeSlot -> CostumeRenderer)
    private val playerRenderers = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, CostumeRenderer>>()

    // UUID -> (CostumeSlot -> CostumeAnimatable)
    private val playerAnimatables = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, CostumeAnimatable>>()

    private val itemRenderers = ConcurrentHashMap<String, CostumeItemRenderer>()
    private val validatedCostumes = ConcurrentHashMap.newKeySet<String>()
    private val brokenCostumes = ConcurrentHashMap.newKeySet<String>()
    private val deferredModels = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun reload() {
        costumeDefinitions.clear()
        playerCostumes.clear()
        playerRenderers.clear()
        playerAnimatables.clear()
        itemRenderers.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
        deferredModels.clear()
    }

    fun addCostume(id: String, wrapper: CostumeModelWrapper) {
        costumeDefinitions[id] = wrapper
        playerRenderers.values.forEach { it.remove(wrapper.slot) }
        playerAnimatables.values.forEach { it.remove(wrapper.slot) }
        itemRenderers.remove(id)
        validatedCostumes.remove(id)
        brokenCostumes.remove(id)
        deferredModels.remove(wrapper.modelLocation)
    }

    fun replaceDefinitions(definitions: Map<String, CostumeModelWrapper>) {
        costumeDefinitions.clear()
        costumeDefinitions.putAll(definitions)
        refreshRenderers()
    }

    fun refreshRenderers() {
        playerRenderers.clear()
        playerAnimatables.clear()
        itemRenderers.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
        deferredModels.clear()
    }

    fun getCostume(costumeId: String): CostumeModelWrapper? = costumeDefinitions[costumeId]

    fun hasAny(playerUUID: UUID): Boolean = playerCostumes[playerUUID]?.isNotEmpty() == true

    fun assignCostume(playerUUID: UUID, costumeId: String) {
        val wrapper = costumeDefinitions[costumeId] ?: return
        val slot = wrapper.slot
        playerCostumes.computeIfAbsent(playerUUID) { ConcurrentHashMap() }[slot] = costumeId
        playerRenderers[playerUUID]?.remove(slot)
        playerAnimatables[playerUUID]?.remove(slot)
    }

    fun removeCostume(playerUUID: UUID, slot: CostumeSlot? = null) {
        if (slot == null) {
            playerCostumes.remove(playerUUID)
            playerRenderers.remove(playerUUID)
            playerAnimatables.remove(playerUUID)
        } else {
            playerCostumes[playerUUID]?.remove(slot)
            playerRenderers[playerUUID]?.remove(slot)
            playerAnimatables[playerUUID]?.remove(slot)
        }
    }

    fun getItemRenderer(costumeId: String): CostumeItemRenderer? {
        val wrapper = costumeDefinitions[costumeId] ?: return null
        return itemRenderers.computeIfAbsent(costumeId) { CostumeItemRenderer(wrapper) }
    }

    fun renderForPlayer(
        entity: Entity,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        partialTick: Float,
        boneSnapshot: PlayerBoneSnapshot
    ) {
        val slots = playerCostumes[entity.uuid] ?: return
        val renderers = playerRenderers.computeIfAbsent(entity.uuid) { ConcurrentHashMap() }
        val animatables = playerAnimatables.computeIfAbsent(entity.uuid) { ConcurrentHashMap() }

        slots.forEach { (slot, costumeId) ->
            if (brokenCostumes.contains(costumeId)) {
                return@forEach
            }
            val wrapper = costumeDefinitions[costumeId] ?: return@forEach
            if (!isCostumeReady(costumeId, wrapper)) {
                return@forEach
            }

            val animatable = animatables.computeIfAbsent(slot) { CostumeAnimatable() }
            val renderer = renderers.computeIfAbsent(slot) { CostumeRenderer(wrapper, animatable) }
            renderer.currentBoneSnapshot = boneSnapshot
            val depth = PoseStackDepth.capture(poseStack)
            try {
                renderer.render(entity, poseStack, bufferSource, packedLight, partialTick)
            } catch (ex: RuntimeException) {
                PoseStackDepth.restore(poseStack, depth)
                brokenCostumes.add(costumeId)
                renderers.remove(slot)
                animatables.remove(slot)
                Lantern.logger.error("[Lantern] Disabled broken costume renderer: {}", costumeId, ex)
            }
        }
    }

    private fun isCostumeReady(costumeId: String, wrapper: CostumeModelWrapper): Boolean {
        if (!validatedCostumes.contains(costumeId)) {
            val manager = Minecraft.getInstance().resourceManager
            if (manager.getResource(wrapper.modelLocation).isEmpty) {
                brokenCostumes.add(costumeId)
                Lantern.logger.warn("[Lantern] Costume model resource missing: {} ({})", wrapper.modelLocation, costumeId)
                return false
            }
            if (wrapper.textureUrl == null && manager.getResource(wrapper.textureLocation).isEmpty) {
                brokenCostumes.add(costumeId)
                Lantern.logger.warn("[Lantern] Costume texture resource missing: {} ({})", wrapper.textureLocation, costumeId)
                return false
            }
            validatedCostumes.add(costumeId)
        }

        if (GeckoLibCache.getBakedModels()[wrapper.modelLocation] == null) {
            if (deferredModels.add(wrapper.modelLocation)) {
                Lantern.logger.warn("[Lantern] Costume model not yet cached, deferring render: {}", wrapper.modelLocation)
            }
            return false
        }
        return true
    }

    private object PoseStackDepth {
        fun capture(poseStack: PoseStack): Int {
            return (poseStack as PoseStackAccessor).`lantern$getPoseStack`().size
        }

        fun restore(poseStack: PoseStack, depth: Int) {
            val deque = (poseStack as PoseStackAccessor).`lantern$getPoseStack`()
            while (deque.size > depth) {
                poseStack.popPose()
            }
        }
    }

    /** Remove all cached data for a specific player. Called when entity unloads. */
    fun removePlayer(playerUUID: UUID) {
        playerCostumes.remove(playerUUID)
        playerRenderers.remove(playerUUID)
        playerAnimatables.remove(playerUUID)
    }
}
