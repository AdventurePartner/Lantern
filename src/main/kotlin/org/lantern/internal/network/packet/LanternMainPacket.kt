package org.lantern.internal.network.packet

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern

data class LanternMainPacket(
    val data: ByteArray
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<LanternMainPacket> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<LanternMainPacket> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "main")
        )

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, LanternMainPacket> = StreamCodec.of(
            { buf, packet ->
                buf.writeBytes(packet.data)
            },
            { buf ->
                val readable = buf.readableBytes()
                val bytes = ByteArray(readable)
                buf.readBytes(bytes)
                LanternMainPacket(bytes)
            }
        )
    }
}
