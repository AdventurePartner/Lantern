package org.lantern.channel

import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.lantern.handler.CacheHandler
import org.lantern.util.PlayerUtils.executeCommands
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class LanternChannelMessageListener : PluginMessageListener {

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray
    ) {
        ByteArrayInputStream(message).use {
            DataInputStream(it).use { dataInputStream ->
                val packetId = dataInputStream.readByte().toInt()
                when (packetId) {
                    1 -> resolveKeyboards(player, dataInputStream)
                }
            }
        }
    }

    private fun resolveKeyboards(player: Player, dataInputStream: DataInputStream) {
        val length = dataInputStream.readInt()
        val bytes = ByteArray(length)
        dataInputStream.read(bytes)
        val press = dataInputStream.readBoolean()
        val key = String(bytes, Charsets.UTF_8)
        CacheHandler.keys[key]?.takeIf { it.press == press }?.let { player.executeCommands(it.commands) }
    }
}