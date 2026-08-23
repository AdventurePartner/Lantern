package org.lantern.internal.network.packet

import com.google.gson.JsonObject
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
import org.lantern.internal.util.JsonUtils

data class LanternDataPacket(
    val packetId: Int,
    val obj: JsonObject
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<LanternDataPacket> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<LanternDataPacket> = CustomPacketPayload.Type(
            IdentifierBridge.of(Lantern.MOD_ID, "data")
        )

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, LanternDataPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) },
            { buf -> decode(buf) }
        )

        private fun decode(buffer: RegistryFriendlyByteBuf): LanternDataPacket {
            val packetId = buffer.readInt()
            val length = buffer.readInt()
            val raw = ByteArray(length)
            buffer.readBytes(raw)

            val message = String(raw, Charsets.UTF_8)
            val obj = JsonUtils.fromString(message)
            return LanternDataPacket(packetId, obj)
        }

        private fun encode(packet: LanternDataPacket, buffer: RegistryFriendlyByteBuf) {
            buffer.writeInt(packet.packetId)
            val bytes = packet.obj.toString().toByteArray(Charsets.UTF_8)
            buffer.writeInt(bytes.size)
            buffer.writeBytes(bytes)
        }
    }
}