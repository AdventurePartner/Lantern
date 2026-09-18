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
        assignDefaultPlayerCostume(event.player)
        sendInitialPacketsIfReady(event.player)
    }

    /**
     * P1 玩家宿主化：进服自动套整替外观（costumes.yml 顶层 player-default.costume）。
     * 玩家已有 full_body 分配时不覆盖——手动/衣橱分配优先；分配后随初始包一起下发
     */
    private fun assignDefaultPlayerCostume(player: Player) {
        val costumeId = org.lantern.handler.CacheHandler.defaultPlayerCostume ?: return
        if (org.lantern.handler.CacheHandler.costumes[costumeId]?.hostDriven != true) return
        val uuid = player.uniqueId
        val current = org.lantern.handler.CostumeAssignmentHandler.get(uuid)
        if (current != null && current["full_body"] != null) return
        org.lantern.handler.CostumeAssignmentHandler.assign(uuid, "full_body", costumeId)
        NetworkHandler.broadcastCostumeAssignment()
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
        org.lantern.animation.AnimationGroupService.forget(uuid)
        org.lantern.animation.AnimationOrchestrator.clearSuppression(uuid)
        // 绑定的已同步集合摘掉该观察者：重新进服时会重新收到现存绑定
        org.lantern.bind.BindRegistry.forgetViewer(uuid)
        // 输入锁是会话内状态，不跨登录保留
        org.lantern.input.InputLockManager.forget(uuid)
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
