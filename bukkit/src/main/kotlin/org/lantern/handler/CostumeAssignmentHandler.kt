package org.lantern.handler

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeAssignmentHandler {
    // Player UUID -> (slot -> costumeId)
    private val assignments = ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>>()

    fun assign(playerUUID: UUID, slot: String, costumeId: String) {
        assignments.computeIfAbsent(playerUUID) { ConcurrentHashMap() }[slot] = costumeId
    }

    fun remove(playerUUID: UUID, slot: String? = null) {
        if (slot == null) {
            assignments.remove(playerUUID)
        } else {
            assignments[playerUUID]?.remove(slot)
        }
    }

    fun get(playerUUID: UUID): Map<String, String>? = assignments[playerUUID]?.toMap()

    fun getAll(): Map<UUID, Map<String, String>> = assignments.mapValues { it.value.toMap() }

    fun clear() { assignments.clear() }
}
