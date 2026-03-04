@file:Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")

package org.lantern.internal.network

import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.internal.network.packet.KeyboardPacket
import org.lantern.internal.network.packet.LanternDataPacket
import org.lantern.internal.network.packet.LanternMainPacket
import org.lantern.internal.util.JsonUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object PacketNetwork {
    val LANTERN_DATA_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "data")
    val KEYBOARD_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "keyboard")
    val LANTERN_MAIN_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "main")

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
                decodeMainS2C(data)?.let { (packetId, obj) ->
                    NetworkParser.parse(packetId, obj)
                }
            }
        }

        // 注册 C2S (Client to Server) 键盘包
        PayloadTypeRegistry.playC2S().register(KeyboardPacket.TYPE, KeyboardPacket.CODEC)
        PayloadTypeRegistry.playC2S().register(LanternMainPacket.TYPE, LanternMainPacket.CODEC)
    }

    fun sendKeyboardPacket(key: String, press: Boolean, inGui: Boolean = false) {
        if (ClientPlayNetworking.canSend(LanternMainPacket.TYPE)) {
            val data = encodeMainC2SKeyboard(key, press, inGui)
            ClientPlayNetworking.send(LanternMainPacket(data))
            return
        }
        if (ClientPlayNetworking.canSend(KeyboardPacket.TYPE)) {
            ClientPlayNetworking.send(KeyboardPacket(key, press, inGui))
        }
    }

    private fun decodeMainS2C(data: ByteArray): Pair<Int, JsonObject>? {
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                val packetType = input.readByte().toInt()
                if (packetType != 0) {
                    return null
                }
                val packetId = input.readInt()
                val raw = input.readAllBytes()
                val obj = JsonUtils.fromString(String(raw, Charsets.UTF_8))
                packetId to obj
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeMainC2SKeyboard(key: String, press: Boolean, inGui: Boolean = false): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(1)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            data.writeInt(keyBytes.size)
            data.write(keyBytes)
            data.writeBoolean(press)
            data.writeBoolean(inGui)
        }
        return output.toByteArray()
    }
}