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

    private const val S2C_JSON_PACKET_TYPE = 0
    private const val S2C_CHUNK_PACKET_TYPE = 2

    private data class PendingMainChunk(
        val totalChunks: Int,
        val originalLength: Int,
        val chunks: Array<ByteArray?>,
        var receivedChunks: Int = 0,
        var receivedBytes: Int = 0
    )

    private val pendingMainChunks = mutableMapOf<Int, PendingMainChunk>()

    fun clearPendingMainChunks() {
        pendingMainChunks.clear()
    }

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
        if (data.isEmpty()) return null

        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                when (input.readByte().toInt()) {
                    S2C_JSON_PACKET_TYPE -> decodeMainJsonPacket(input)
                    S2C_CHUNK_PACKET_TYPE -> decodeMainChunkPacket(input)
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeMainJsonPacket(input: DataInputStream): Pair<Int, JsonObject> {
        val packetId = input.readInt()
        val raw = input.readAllBytes()
        val obj = JsonUtils.fromString(String(raw, Charsets.UTF_8))
        return packetId to obj
    }

    private fun decodeMainChunkPacket(input: DataInputStream): Pair<Int, JsonObject>? {
        val messageId = input.readInt()
        val totalChunks = input.readInt()
        val chunkIndex = input.readInt()
        val originalLength = input.readInt()
        val chunkLength = input.readInt()
        val chunk = input.readAllBytes()

        if (
            totalChunks <= 0 ||
            chunkIndex !in 0 until totalChunks ||
            originalLength <= 0 ||
            chunkLength < 0 ||
            chunk.size != chunkLength
        ) {
            return null
        }

        var pending = pendingMainChunks[messageId]
        if (
            pending == null ||
            pending.totalChunks != totalChunks ||
            pending.originalLength != originalLength
        ) {
            pending = PendingMainChunk(totalChunks, originalLength, arrayOfNulls(totalChunks))
            pendingMainChunks[messageId] = pending
        }

        if (pending.chunks[chunkIndex] != null) return null

        pending.chunks[chunkIndex] = chunk
        pending.receivedChunks++
        pending.receivedBytes += chunkLength

        if (pending.receivedChunks != totalChunks) return null

        val assembled = ByteArray(originalLength)
        var offset = 0
        for (storedChunk in pending.chunks) {
            if (storedChunk == null || offset + storedChunk.size > originalLength) {
                pendingMainChunks.remove(messageId)
                return null
            }
            storedChunk.copyInto(assembled, offset)
            offset += storedChunk.size
        }
        pendingMainChunks.remove(messageId)

        if (offset != originalLength || pending.receivedBytes != originalLength) return null

        return decodeMainS2C(assembled)
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