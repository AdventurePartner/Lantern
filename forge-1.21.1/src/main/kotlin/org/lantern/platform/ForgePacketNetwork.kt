package org.lantern.platform

import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.event.network.CustomPayloadEvent
import net.minecraftforge.network.ChannelBuilder
import net.minecraftforge.network.EventNetworkChannel
import org.lantern.core.protocol.LanternProtocol
import org.lantern.core.protocol.MainPayloadDecoder
import org.lantern.internal.network.NetworkParser
import org.lantern.internal.util.JsonUtils
import java.util.function.Consumer

object ForgePacketNetwork {
    private val mainPayloadDecoder = MainPayloadDecoder()
    private val CHANNEL: EventNetworkChannel = ChannelBuilder
        .named(IdentifierBridge.of("lantern", "main"))
        .networkProtocolVersion(1)
        .optional()
        .eventNetworkChannel()

    fun handleMainPayload(bytes: ByteArray) {
        mainPayloadDecoder.decode(bytes)?.let { packet ->
            NetworkParser.parse(packet.packetId, JsonUtils.fromString(packet.json))
        }
    }

    fun clear() {
        mainPayloadDecoder.clear()
    }

    fun registerPackets() {
        CHANNEL.addListener(Consumer<CustomPayloadEvent> { event ->
            if (!event.source.isClientSide) {
                return@Consumer
            }

            val payload = event.payload!!
            val bytes = ByteArray(payload.readableBytes())
            payload.readBytes(bytes)

            event.source.enqueueWork {
                handleMainPayload(bytes)
            }
            event.source.setPacketHandled(true)
        })
    }

    fun sendKeyboardPacket(key: String, press: Boolean, inGui: Boolean) {
        sendMain(LanternProtocol.encodeKeyboardC2S(key, press, inGui))
    }

    /** C2S 动画生命周期事件上报（type 3），由 NetworkParser.animationEventSender 调用 */
    fun sendAnimationEvent(uuid: java.util.UUID, animation: String, event: String) {
        sendMain(LanternProtocol.encodeAnimationEventC2S(uuid.toString(), animation, event))
    }

    /** 没有连接（标题画面）或远端没注册 lantern:main 时静默丢弃；PacketDistributor.SERVER 不判空会 NPE */
    private fun sendMain(data: ByteArray) {
        val connection = Minecraft.getInstance().connection?.connection ?: return
        if (!CHANNEL.isRemotePresent(connection)) return
        CHANNEL.send(FriendlyByteBuf(Unpooled.wrappedBuffer(data)), connection)
    }
}
