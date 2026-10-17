package org.lantern.platform

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.PacketFlow
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import org.lantern.core.protocol.LanternProtocol
import org.lantern.core.protocol.MainPayloadDecoder
import org.lantern.internal.network.NetworkParser
import org.lantern.internal.util.JsonUtils
import java.util.UUID
import java.util.function.Consumer

object NeoForgePacketNetwork {
    private val mainPayloadDecoder = MainPayloadDecoder()

    fun register(modBus: IEventBus) {
        modBus.addListener(
            RegisterPayloadHandlersEvent::class.java,
            Consumer<RegisterPayloadHandlersEvent>(::registerPayloads)
        )
    }

    // optional：服务端是 Bukkit/Paper，不存在 NeoForge 的频道协商
    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1")
            .optional()
            .playBidirectional(LanternMainPayload.TYPE, LanternMainPayload.STREAM_CODEC, ::handleMainPayload)
    }

    // 21.1 的处理器默认在主线程执行
    private fun handleMainPayload(payload: LanternMainPayload, context: IPayloadContext) {
        if (context.flow() != PacketFlow.CLIENTBOUND) return
        mainPayloadDecoder.decode(payload.data)?.let { packet ->
            NetworkParser.parse(packet.packetId, JsonUtils.fromString(packet.json))
        }
    }

    fun clear() {
        mainPayloadDecoder.clear()
    }

    fun sendKeyboardPacket(key: String, press: Boolean, inGui: Boolean) {
        sendMain(LanternProtocol.encodeKeyboardC2S(key, press, inGui))
    }

    /** C2S 动画生命周期事件上报（type 3），由 NetworkParser.animationEventSender 调用 */
    fun sendAnimationEvent(uuid: UUID, animation: String, event: String) {
        sendMain(LanternProtocol.encodeAnimationEventC2S(uuid.toString(), animation, event))
    }

    /** 没有连接（标题画面）或服务端没注册 lantern:main 时静默丢弃 */
    private fun sendMain(data: ByteArray) {
        val connection = Minecraft.getInstance().connection ?: return
        if (!connection.hasChannel(LanternMainPayload.TYPE)) return
        PacketDistributor.sendToServer(LanternMainPayload(data))
    }
}
