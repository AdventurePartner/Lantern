package org.lantern.costume.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.Entity
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderer.CostumeItemRenderer
import org.lantern.costume.renderer.CostumeRenderer
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
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

    fun reload() {
        costumeDefinitions.clear()
        playerCostumes.clear()
        playerRenderers.clear()
        playerAnimatables.clear()
        itemRenderers.clear()
    }

    fun addCostume(id: String, wrapper: CostumeModelWrapper) {
        costumeDefinitions[id] = wrapper
    }

    fun getCostume(costumeId: String): CostumeModelWrapper? = costumeDefinitions[costumeId]

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
            val wrapper = costumeDefinitions[costumeId] ?: return@forEach
            val animatable = animatables.computeIfAbsent(slot) { CostumeAnimatable() }
            val renderer = renderers.computeIfAbsent(slot) { CostumeRenderer(wrapper, animatable) }
            renderer.currentBoneSnapshot = boneSnapshot
            renderer.render(entity, poseStack, bufferSource, packedLight, partialTick)
        }
    }

    /** Remove all cached data for a specific player. Called when entity unloads. */
    fun removePlayer(playerUUID: UUID) {
        playerCostumes.remove(playerUUID)
        playerRenderers.remove(playerUUID)
        playerAnimatables.remove(playerUUID)
    }
}
