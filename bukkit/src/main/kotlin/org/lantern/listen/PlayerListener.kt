package org.lantern.listen

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRegisterChannelEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler
import java.util.UUID

class PlayerListener : Listener {
    private val joinedPlayers = mutableSetOf<UUID>()
    private val syncedPlayers = mutableSetOf<UUID>()

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        joinedPlayers.add(event.player.uniqueId)
        sendInitialPacketsIfReady(event.player)
    }

    @EventHandler
    fun onChannelRegistered(event: PlayerRegisterChannelEvent) {
        if (event.channel == MAIN_CHANNEL) {
            sendInitialPacketsIfReady(event.player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        joinedPlayers.remove(uuid)
        syncedPlayers.remove(uuid)
        LanternPlugin.instance.channelListener.removePlayer(uuid)
        org.lantern.camera.ShoulderCameraService.removePlayer(uuid)
        org.lantern.camera.CameraPathService.removePlayer(uuid)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChangedWorld(event: PlayerChangedWorldEvent) {
        NetworkHandler.sendBlockPositions(event.player)
    }

    private fun sendInitialPacketsIfReady(player: Player) {
        val uuid = player.uniqueId
        if (uuid !in joinedPlayers || uuid in syncedPlayers || MAIN_CHANNEL !in player.listeningPluginChannels) {
            return
        }
        NetworkHandler.sendPackets(player)
        syncedPlayers.add(uuid)
    }

    private companion object {
        const val MAIN_CHANNEL = "lantern:main"
    }
}
