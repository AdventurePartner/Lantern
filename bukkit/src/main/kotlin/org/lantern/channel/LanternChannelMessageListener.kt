package org.lantern.channel

import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.lantern.animation.AnimationOrchestrator
import org.lantern.core.protocol.LanternProtocol
import org.lantern.handler.CacheHandler
import org.lantern.util.PlayerUtils.executeCommands
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LanternChannelMessageListener : PluginMessageListener {

    // Per-player last message timestamp for rate limiting
    private val lastMessageTime = ConcurrentHashMap<UUID, Long>()
    private val COOLDOWN_MS = 50L

    /**
     * 动画事件（type 3）限流：按 200ms 窗口计数，超过上限丢弃。
     * 不能用键盘包那种「两包间隔 < 50ms 即丢」——一次翻滚起手会在同一毫秒连发
     * suppress/invuln 两条，finish 与下一次起手也常挤在同一 tick
     */
    private class EventWindow(var startMs: Long, var count: Int)
    private val eventWindows = ConcurrentHashMap<UUID, EventWindow>()
    private val EVENT_WINDOW_MS = 200L
    private val EVENT_WINDOW_LIMIT = 24

    fun removePlayer(uuid: UUID) {
        lastMessageTime.remove(uuid)
        eventWindows.remove(uuid)
    }

    private fun allowEvent(uuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val window = eventWindows.computeIfAbsent(uuid) { EventWindow(now, 0) }
        if (now - window.startMs > EVENT_WINDOW_MS) {
            window.startMs = now
            window.count = 0
        }
        window.count++
        return window.count <= EVENT_WINDOW_LIMIT
    }

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray
    ) {
        when (message.firstOrNull()?.toInt()) {
            LanternProtocol.C2S_KEYBOARD_PACKET_TYPE -> {
                // 键盘包保留 50ms 限流
                val now = System.currentTimeMillis()
                val lastTime = lastMessageTime.put(player.uniqueId, now)
                if (lastTime != null && now - lastTime < COOLDOWN_MS) return
                // 长度字段是客户端写的：共享解码器限长并校验剩余字节，坏包直接丢
                val packet = LanternProtocol.decodeKeyboardC2S(message) ?: return
                resolveKeyboard(player, packet.key, packet.press, packet.inGui)
            }
            LanternProtocol.C2S_ANIMATION_EVENT_PACKET_TYPE -> {
                // 动画生命周期事件。编码: type(1) + utf uuid + utf animation + utf event。
                // 发包玩家一并传下去：suppress/invuln/request 只能作用于发包者本人，
                // finish 对实体的情况要校验发包者确实在该实体附近——包里的 UUID 是客户端写的，不可信
                if (!allowEvent(player.uniqueId)) return
                val packet = LanternProtocol.decodeAnimationEventC2S(message) ?: return
                AnimationOrchestrator.onClientFinished(player, packet.uuid, packet.animation, packet.event)
            }
        }
    }

    private fun resolveKeyboard(player: Player, key: String, press: Boolean, inGui: Boolean) {
        // 相机调节键优先消化（返回 true = 相机已处理，不再走 keys.yml 命令派发）
        if (org.lantern.camera.ShoulderCameraService.handleKey(player, key, press, inGui)) return
        CacheHandler.keys[key]
            ?.takeIf { it.press == press && (it.inGui || !inGui) }
            ?.let { player.executeCommands(it.commands) }
    }
}