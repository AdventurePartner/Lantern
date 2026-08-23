package org.lantern.platform

import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.fml.LogicalSide
import org.lantern.core.protocol.LanternProtocol
import org.lantern.core.protocol.MainPayloadDecoder
import org.lantern.internal.network.NetworkParser
import org.lantern.internal.util.JsonUtils
import java.util.function.Consumer

object ForgePacketNetwork {
    private val mainPayloadDecoder = MainPayloadDecoder()
    private val CHANNEL = NetworkRegistry.ChannelBuilder
        .named(IdentifierBridge.of("lantern", "main"))
        .networkProtocolVersion { "1" }
        .clientAcceptedVersions { true }
        .serverAcceptedVersions { true }
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
        // 保留 EventNetworkChannel 注册，让 Forge 在 vanilla/Bukkit 连接上通告 lantern:main。
        // 实际 S2C 数据由 ClientPacketListener1201Mixin 在 vanilla unknown-channel 分支前拦截。
        CHANNEL.addListener(Consumer<NetworkEvent.ClientCustomPayloadEvent> { event ->
            val payload = event.payload
            val bytes = ByteArray(payload.readableBytes())
            payload.readBytes(bytes)

            val context = event.source.get()
            if (context.direction.receptionSide != LogicalSide.CLIENT) {
                context.setPacketHandled(true)
                return@Consumer
            }

            context.enqueueWork {
                handleMainPayload(bytes)
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
