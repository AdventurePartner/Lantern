package org.lantern.handler

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeAssignmentHandler {
    // Player UUID -> Costume ID
    private val assignments = ConcurrentHashMap<UUID, String>()

    fun assign(playerUUID: UUID, costumeId: String) { assignments[playerUUID] = costumeId }
    fun remove(playerUUID: UUID) { assignments.remove(playerUUID) }
    fun get(playerUUID: UUID): String? = assignments[playerUUID]
    fun getAll(): Map<UUID, String> = assignments.toMap()
    fun clear() { assignments.clear() }
}
