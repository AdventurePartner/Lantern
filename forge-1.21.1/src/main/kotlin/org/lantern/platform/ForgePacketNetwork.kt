package org.lantern.platform

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.event.network.CustomPayloadEvent
import net.minecraftforge.network.ChannelBuilder
import net.minecraftforge.network.EventNetworkChannel
import net.minecraftforge.network.PacketDistributor
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
        val data = LanternProtocol.encodeKeyboardC2S(key, press, inGui)
        CHANNEL.send(FriendlyByteBuf(Unpooled.wrappedBuffer(data)), PacketDistributor.SERVER.noArg())
    }
}
