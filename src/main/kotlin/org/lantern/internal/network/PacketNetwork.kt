@file:Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")

package org.lantern.internal.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.core.protocol.LanternProtocol
import org.lantern.core.protocol.MainPayloadDecoder
import org.lantern.internal.network.packet.KeyboardPacket
import org.lantern.internal.network.packet.LanternDataPacket
import org.lantern.internal.network.packet.LanternMainPacket
import org.lantern.internal.util.JsonUtils
import org.lantern.platform.IdentifierBridge

object PacketNetwork {
    val LANTERN_DATA_ID: ResourceLocation = IdentifierBridge.of(Lantern.MOD_ID, "data")
    val KEYBOARD_ID: ResourceLocation = IdentifierBridge.of(Lantern.MOD_ID, "keyboard")
    val LANTERN_MAIN_ID: ResourceLocation = IdentifierBridge.of(Lantern.MOD_ID, "main")

    private val mainPayloadDecoder = MainPayloadDecoder()

    fun clearPendingMainChunks() {
        mainPayloadDecoder.clear()
    }

    fun registerPackets() {
        PayloadTypeRegistry.playS2C().register(LanternDataPacket.TYPE, LanternDataPacket.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(LanternDataPacket.TYPE) { payload, context ->
            context.client().execute {
                NetworkParser.parse(payload.packetId, payload.obj)
            }
        }

        PayloadTypeRegistry.playS2C().register(LanternMainPacket.TYPE, LanternMainPacket.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(LanternMainPacket.TYPE) { payload, context ->
            context.client().execute {
                mainPayloadDecoder.decode(payload.data)?.let { packet ->
                    NetworkParser.parse(packet.packetId, JsonUtils.fromString(packet.json))
                }
            }
        }

        PayloadTypeRegistry.playC2S().register(KeyboardPacket.TYPE, KeyboardPacket.CODEC)
        PayloadTypeRegistry.playC2S().register(LanternMainPacket.TYPE, LanternMainPacket.CODEC)
    }

    fun sendKeyboardPacket(key: String, press: Boolean, inGui: Boolean = false) {
        if (ClientPlayNetworking.canSend(LanternMainPacket.TYPE)) {
            ClientPlayNetworking.send(LanternMainPacket(LanternProtocol.encodeKeyboardC2S(key, press, inGui)))
            return
        }
        if (ClientPlayNetworking.canSend(KeyboardPacket.TYPE)) {
            ClientPlayNetworking.send(KeyboardPacket(key, press, inGui))
        }
    }
}
