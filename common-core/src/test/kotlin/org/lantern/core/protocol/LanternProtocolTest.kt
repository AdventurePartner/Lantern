package org.lantern.core.protocol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanternProtocolTest {
    @Test
    fun `decodes an unchunked Bukkit payload`() {
        val decoder = MainPayloadDecoder()
        val packet = decoder.decode(LanternProtocol.encodeMainS2C(14, "{\"ok\":true}"))

        assertEquals(MainS2CPacket(14, "{\"ok\":true}"), packet)
    }

    @Test
    fun `reassembles chunks received out of order`() {
        val decoder = MainPayloadDecoder()
        val payload = LanternProtocol.encodeMainS2C(2, "x".repeat(70_000))
        val chunks = payload.asList().chunked(32_000).map { bytes -> bytes.toByteArray() }

        assertNull(decoder.decode(chunk(7, chunks.size, 1, payload.size, chunks[1])))
        assertNull(decoder.decode(chunk(7, chunks.size, 0, payload.size, chunks[0])))
        val packet = decoder.decode(chunk(7, chunks.size, 2, payload.size, chunks[2]))

        assertEquals(2, packet?.packetId)
        assertEquals("x".repeat(70_000), packet?.json)
    }

    @Test
    fun `clear discards incomplete messages`() {
        val decoder = MainPayloadDecoder()
        val payload = LanternProtocol.encodeMainS2C(2, "x".repeat(40_000))
        val first = payload.copyOfRange(0, 32_000)
        val second = payload.copyOfRange(32_000, payload.size)

        assertNull(decoder.decode(chunk(9, 2, 0, payload.size, first)))
        decoder.clear()
        assertNull(decoder.decode(chunk(9, 2, 1, payload.size, second)))
    }

    @Test
    fun `keyboard payload keeps Bukkit wire format`() {
        val encoded = LanternProtocol.encodeKeyboardC2S("ctrl+k", true, true)

        assertEquals(KeyboardC2SPacket("ctrl+k", true, true), LanternProtocol.decodeKeyboardC2S(encoded))
    }

    private fun chunk(
        messageId: Int,
        totalChunks: Int,
        chunkIndex: Int,
        originalLength: Int,
        bytes: ByteArray
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(LanternProtocol.S2C_CHUNK_PACKET_TYPE)
            data.writeInt(messageId)
            data.writeInt(totalChunks)
            data.writeInt(chunkIndex)
            data.writeInt(originalLength)
            data.writeInt(bytes.size)
            data.write(bytes)
        }
        return output.toByteArray()
    }
}
