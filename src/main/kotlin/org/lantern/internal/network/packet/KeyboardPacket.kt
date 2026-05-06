package org.lantern.internal.network.packet

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern

data class KeyboardPacket(
    val key: String,
    val press: Boolean,
    val inGui: Boolean = false
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<KeyboardPacket> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<KeyboardPacket> = CustomPacketPayload.Type(
            IdentifierBridge.of(Lantern.MOD_ID, "keyboard")
        )

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, KeyboardPacket> = StreamCodec.of(
            { buf, packet -> encode(packet, buf) },
            { buf -> decode(buf) }
        )

        private fun decode(buffer: RegistryFriendlyByteBuf): KeyboardPacket {
            val key = buffer.readUtf()
            val press = buffer.readBoolean()
            val inGui = buffer.readBoolean()
            return KeyboardPacket(key, press, inGui)
        }

        private fun encode(packet: KeyboardPacket, buffer: RegistryFriendlyByteBuf) {
            buffer.writeUtf(packet.key)
            buffer.writeBoolean(packet.press)
            buffer.writeBoolean(packet.inGui)
        }
    }
}