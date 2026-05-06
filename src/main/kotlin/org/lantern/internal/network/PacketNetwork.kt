@file:Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")

package org.lantern.internal.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
import org.lantern.core.protocol.LanternProtocol
import org.lantern.internal.network.packet.KeyboardPacket
import org.lantern.internal.network.packet.LanternDataPacket
import org.lantern.internal.network.packet.LanternMainPacket
import org.lantern.internal.util.JsonUtils

object PacketNetwork {
    val LANTERN_DATA_ID: ResourceLocation = IdentifierBridge.of(Lantern.MOD_ID, "data")
    val KEYBOARD_ID: ResourceLocation = IdentifierBridge.of(Lantern.MOD_ID, "keyboard")
    val LANTERN_MAIN_ID: ResourceLocation = IdentifierBridge.of(Lantern.MOD_ID, "main")

    fun registerPackets() {
        // 注册 S2C (Server to Client) 数据包
        PayloadTypeRegistry.playS2C().register(LanternDataPacket.TYPE, LanternDataPacket.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(LanternDataPacket.TYPE) { payload, context ->
            context.client().execute {
                NetworkParser.parse(payload.packetId, payload.obj)
            }
        }

        // Bukkit plugin channel bridge (lantern:main)
        PayloadTypeRegistry.playS2C().register(LanternMainPacket.TYPE, LanternMainPacket.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(LanternMainPacket.TYPE) { payload, context ->
            val data = payload.data
            context.client().execute {
                LanternProtocol.decodeMainS2C(data)?.let { packet ->
                    NetworkParser.parse(packet.packetId, JsonUtils.fromString(packet.json))
                }
            }
        }

        // 注册 C2S (Client to Server) 键盘包
        PayloadTypeRegistry.playC2S().register(KeyboardPacket.TYPE, KeyboardPacket.CODEC)
        PayloadTypeRegistry.playC2S().register(LanternMainPacket.TYPE, LanternMainPacket.CODEC)
    }

    fun sendKeyboardPacket(key: String, press: Boolean, inGui: Boolean = false) {
        if (ClientPlayNetworking.canSend(LanternMainPacket.TYPE)) {
            val data = LanternProtocol.encodeKeyboardC2S(key, press, inGui)
            ClientPlayNetworking.send(LanternMainPacket(data))
            return
        }
        if (ClientPlayNetworking.canSend(KeyboardPacket.TYPE)) {
            ClientPlayNetworking.send(KeyboardPacket(key, press, inGui))
        }
    }

}
