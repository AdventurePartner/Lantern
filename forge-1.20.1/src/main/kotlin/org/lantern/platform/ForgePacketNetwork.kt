package org.lantern.platform

import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import org.lantern.core.protocol.LanternProtocol
import org.lantern.internal.network.NetworkParser
import org.lantern.internal.util.JsonUtils
import java.util.function.Consumer

object ForgePacketNetwork {
    private val CHANNEL = NetworkRegistry.ChannelBuilder
        .named(IdentifierBridge.of("lantern", "main"))
        .networkProtocolVersion { "1" }
        .clientAcceptedVersions { true }
        .serverAcceptedVersions { true }
        .eventNetworkChannel()

    fun registerPackets() {
        CHANNEL.addListener(Consumer<NetworkEvent.ClientCustomPayloadEvent> { event ->
            val payload = event.payload
            val bytes = ByteArray(payload.readableBytes())
            payload.readBytes(bytes)

            val context = event.source.get()
            context.enqueueWork {
                LanternProtocol.decodeMainS2C(bytes)?.let { packet ->
                    NetworkParser.parse(packet.packetId, JsonUtils.fromString(packet.json))
                }
            }
            context.setPacketHandled(true)
        })
    }

    fun sendKeyboardPacket(key: String, press: Boolean, inGui: Boolean) {
        val data = LanternProtocol.encodeKeyboardC2S(key, press, inGui)
        val packet = ServerboundCustomPayloadPacket(
            IdentifierBridge.of("lantern", "main"),
            FriendlyByteBuf(Unpooled.wrappedBuffer(data))
        )
        Minecraft.getInstance().connection?.send(packet)
    }
}
