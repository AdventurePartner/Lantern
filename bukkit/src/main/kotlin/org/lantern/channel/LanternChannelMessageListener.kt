package org.lantern.channel

import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.lantern.animation.AnimationOrchestrator
import org.lantern.handler.CacheHandler
import org.lantern.util.PlayerUtils.executeCommands
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LanternChannelMessageListener : PluginMessageListener {

    // Per-player last message timestamp for rate limiting
    private val lastMessageTime = ConcurrentHashMap<UUID, Long>()
    private val COOLDOWN_MS = 50L

    fun removePlayer(uuid: UUID) {
        lastMessageTime.remove(uuid)
    }

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray
    ) {
        when (message.firstOrNull()?.toInt()) {
            1 -> {
                // 键盘包保留 50ms 限流
                val now = System.currentTimeMillis()
                val lastTime = lastMessageTime.put(player.uniqueId, now)
                if (lastTime != null && now - lastTime < COOLDOWN_MS) return
                ByteArrayInputStream(message).use {
                    DataInputStream(it).use { dataInputStream ->
                        dataInputStream.readByte()
                        resolveKeyboards(player, dataInputStream)
                    }
                }
            }
            3 -> {
                // 动画生命周期事件（稀疏，不限流；finish 被限流会丢链式编排）
                // 编码: type(1) + utf uuid + utf animation + utf event
                ByteArrayInputStream(message).use {
                    DataInputStream(it).use { input ->
                        input.readByte()
                        val uuid = input.readUTF()
                        val animation = input.readUTF()
                        val event = input.readUTF()
                        AnimationOrchestrator.onClientFinished(uuid, animation, event)
                    }
                }
            }
        }
    }

    private fun resolveKeyboards(player: Player, dataInputStream: DataInputStream) {
        val length = dataInputStream.readInt()
        val bytes = ByteArray(length)
        dataInputStream.read(bytes)
        val press = dataInputStream.readBoolean()
        val inGui = if (dataInputStream.available() > 0) dataInputStream.readBoolean() else false
        val key = String(bytes, Charsets.UTF_8)
        CacheHandler.keys[key]
            ?.takeIf { it.press == press && (it.inGui || !inGui) }
            ?.let { player.executeCommands(it.commands) }
    }
}