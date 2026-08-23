package org.lantern.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.LinkedHashMap

data class MainS2CPacket(
    val packetId: Int,
    val json: String
)

data class KeyboardC2SPacket(
    val key: String,
    val press: Boolean,
    val inGui: Boolean
)

class MainPayloadDecoder {
    private data class PendingMessage(
        val totalChunks: Int,
        val originalLength: Int,
        val chunks: Array<ByteArray?>,
        var receivedChunks: Int = 0,
        var receivedBytes: Int = 0
    )

    private val pendingMessages = LinkedHashMap<Int, PendingMessage>()

    fun decode(data: ByteArray): MainS2CPacket? {
        if (data.isEmpty()) return null

        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                when (input.readByte().toInt()) {
                    LanternProtocol.S2C_JSON_PACKET_TYPE -> decodeJson(input)
                    LanternProtocol.S2C_CHUNK_PACKET_TYPE -> decodeChunk(input)
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        pendingMessages.clear()
    }

    private fun decodeJson(input: DataInputStream): MainS2CPacket {
        val packetId = input.readInt()
        return MainS2CPacket(packetId, String(input.readRemainingBytes(), Charsets.UTF_8))
    }

    private fun decodeChunk(input: DataInputStream): MainS2CPacket? {
        val messageId = input.readInt()
        val totalChunks = input.readInt()
        val chunkIndex = input.readInt()
        val originalLength = input.readInt()
        val chunkLength = input.readInt()

        if (
            totalChunks !in 1..MAX_CHUNKS ||
            chunkIndex !in 0 until totalChunks ||
            originalLength !in 1..MAX_MESSAGE_BYTES ||
            chunkLength !in 0..MAX_CHUNK_BYTES ||
            chunkLength > originalLength
        ) {
            return null
        }

        val chunk = input.readRemainingBytes()
        if (chunk.size != chunkLength) return null

        var pending = pendingMessages[messageId]
        if (pending == null) {
            if (pendingMessages.size >= MAX_PENDING_MESSAGES) return null
            pending = PendingMessage(totalChunks, originalLength, arrayOfNulls(totalChunks))
            pendingMessages[messageId] = pending
        } else if (pending.totalChunks != totalChunks || pending.originalLength != originalLength) {
            pendingMessages.remove(messageId)
            return null
        }

        if (pending.chunks[chunkIndex] != null) return null
        if (pending.receivedBytes + chunkLength > originalLength) {
            pendingMessages.remove(messageId)
            return null
        }

        pending.chunks[chunkIndex] = chunk
        pending.receivedChunks++
        pending.receivedBytes += chunkLength

        if (pending.receivedChunks != totalChunks) return null

        pendingMessages.remove(messageId)
        if (pending.receivedBytes != originalLength) return null

        val assembled = ByteArray(originalLength)
        var offset = 0
        pending.chunks.forEach { storedChunk ->
            if (storedChunk == null || offset + storedChunk.size > assembled.size) return null
            storedChunk.copyInto(assembled, offset)
            offset += storedChunk.size
        }
        if (offset != assembled.size) return null

        return decodeSinglePayload(assembled)
    }

    private fun decodeSinglePayload(data: ByteArray): MainS2CPacket? {
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readByte().toInt() != LanternProtocol.S2C_JSON_PACKET_TYPE) return null
                decodeJson(input)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun DataInputStream.readRemainingBytes(): ByteArray {
        val bytes = ByteArray(available())
        readFully(bytes)
        return bytes
    }

    private companion object {
        const val MAX_PENDING_MESSAGES = 128
        const val MAX_CHUNKS = 4096
        const val MAX_CHUNK_BYTES = 32766
        const val MAX_MESSAGE_BYTES = 64 * 1024 * 1024
    }
}

object LanternProtocol {
    const val CHANNEL_MAIN = "lantern:main"
    const val S2C_JSON_PACKET_TYPE = 0
    const val C2S_KEYBOARD_PACKET_TYPE = 1
    const val S2C_CHUNK_PACKET_TYPE = 2

    fun encodeMainS2C(packetId: Int, json: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(S2C_JSON_PACKET_TYPE)
            data.writeInt(packetId)
            data.write(json.toByteArray(Charsets.UTF_8))
        }
        return output.toByteArray()
    }

    fun decodeMainS2C(data: ByteArray): MainS2CPacket? = MainPayloadDecoder().decode(data)

    fun encodeKeyboardC2S(key: String, press: Boolean, inGui: Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(C2S_KEYBOARD_PACKET_TYPE)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            data.writeInt(keyBytes.size)
            data.write(keyBytes)
            data.writeBoolean(press)
            data.writeBoolean(inGui)
        }
        return output.toByteArray()
    }

    fun decodeKeyboardC2S(data: ByteArray): KeyboardC2SPacket? {
        if (data.isEmpty()) return null

        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readByte().toInt() != C2S_KEYBOARD_PACKET_TYPE) return null
                val length = input.readInt()
                if (length !in 0..MAX_KEY_BYTES || length > input.available()) return null
                val bytes = ByteArray(length)
                input.readFully(bytes)
                val press = input.readBoolean()
                val inGui = if (input.available() > 0) input.readBoolean() else false
                KeyboardC2SPacket(String(bytes, Charsets.UTF_8), press, inGui)
            }
        } catch (_: Exception) {
            null
        }
    }

    private const val MAX_KEY_BYTES = 1024
}
