package org.lantern.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class MainS2CPacket(
    val packetId: Int,
    val json: String
)

data class KeyboardC2SPacket(
    val key: String,
    val press: Boolean,
    val inGui: Boolean
)

object LanternProtocol {
    const val CHANNEL_MAIN = "lantern:main"

    fun encodeMainS2C(packetId: Int, json: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(0)
            data.writeInt(packetId)
            data.write(json.toByteArray(Charsets.UTF_8))
        }
        return output.toByteArray()
    }

    fun decodeMainS2C(data: ByteArray): MainS2CPacket? {
        return runCatching {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                val packetType = input.readByte().toInt()
                if (packetType != 0) return null
                val packetId = input.readInt()
                MainS2CPacket(packetId, String(input.readAllBytes(), Charsets.UTF_8))
            }
        }.getOrNull()
    }

    fun encodeKeyboardC2S(key: String, press: Boolean, inGui: Boolean): ByteArray {
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

    fun decodeKeyboardC2S(data: ByteArray): KeyboardC2SPacket? {
        return runCatching {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                val packetId = input.readByte().toInt()
                if (packetId != 1) return null
                val length = input.readInt()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                val press = input.readBoolean()
                val inGui = if (input.available() > 0) input.readBoolean() else false
                KeyboardC2SPacket(String(bytes, Charsets.UTF_8), press, inGui)
            }
        }.getOrNull()
    }
}

