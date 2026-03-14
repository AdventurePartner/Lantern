package org.lantern.costume.handler

import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderer.CostumeItemRenderer
import org.lantern.costume.renderer.CostumeRenderer
import org.lantern.costume.wrapper.CostumeModelWrapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeHandler {
    private val costumeDefinitions = ConcurrentHashMap<String, CostumeModelWrapper>()
    private val playerCostumes = ConcurrentHashMap<UUID, String>()
    private val playerRenderers = ConcurrentHashMap<UUID, CostumeRenderer>()
    private val playerAnimatables = ConcurrentHashMap<UUID, CostumeAnimatable>()
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
        playerCostumes[playerUUID] = costumeId
        playerRenderers.remove(playerUUID)
        playerAnimatables.remove(playerUUID)
    }

    fun removeCostume(playerUUID: UUID) {
        playerCostumes.remove(playerUUID)
        playerRenderers.remove(playerUUID)
        playerAnimatables.remove(playerUUID)
    }

    fun getItemRenderer(costumeId: String): CostumeItemRenderer? {
        val wrapper = costumeDefinitions[costumeId] ?: return null
        return itemRenderers.computeIfAbsent(costumeId) { CostumeItemRenderer(wrapper) }
    }

    fun getRendererForPlayer(playerUUID: UUID): CostumeRenderer? {
        val costumeId = playerCostumes[playerUUID] ?: return null
        val wrapper = costumeDefinitions[costumeId] ?: return null
        return playerRenderers.computeIfAbsent(playerUUID) {
            val animatable = playerAnimatables.computeIfAbsent(playerUUID) { CostumeAnimatable() }
            CostumeRenderer(wrapper, animatable)
        }
    }
}
