package org.lantern.listen

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRegisterChannelEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler
import org.lantern.handler.CostumeAssignmentHandler

class PlayerListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerRegisterChannelEvent) {
        if (event.channel == "lantern:main") {
            NetworkHandler.sendPackets(event.player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        CostumeAssignmentHandler.remove(uuid)
        LanternPlugin.instance.channelListener.removePlayer(uuid)
    }
}