package org.lantern.core.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Random
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanternProtocolTest {

    @Test
    fun `first byte of a small frame is the packet id`() {
        val frame = LanternProtocol.encodeS2C(99, JsonObject().also { it.addProperty("full", true) })

        assertEquals(99, frame[0].toInt())
        assertEquals(3, frame.size)
    }

    @Test
    fun `data before the protocol info packet is dropped`() {
        val decoder = MainPayloadDecoder()
        val frame = LanternProtocol.encodeS2C(7, JsonObject().also { it.addProperty("key", "k") })

        assertNull(decoder.decode(frame))
        val info = decoder.decode(LanternProtocol.encodeProtocolInfo())
        assertEquals(LanternProtocol.PROTOCOL_INFO_PACKET_ID, info?.packetId)
        assertEquals(LanternProtocol.PROTOCOL_VERSION, info?.payload?.get("version")?.asInt)
        assertEquals("k", decoder.decode(frame)?.payload?.get("key")?.asString)
    }

    @Test
    fun `a different protocol version is reported and blocks data`() {
        val decoder = MainPayloadDecoder()
        val info = decoder.decode(byteArrayOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID.toByte(), 3))

        assertEquals(3, info?.payload?.get("version")?.asInt)
        assertNull(decoder.decode(LanternProtocol.encodeS2C(7, JsonObject().also { it.addProperty("key", "k") })))
    }

    @Test
    fun `legacy JSON frames are reported once as version 1`() {
        val decoder = MainPayloadDecoder()
        val legacy = legacyJsonFrame(1, "{\"characters\":[]}")

        assertEquals(LanternProtocol.LEGACY_JSON_PROTOCOL_VERSION, decoder.decode(legacy)?.payload?.get("version")?.asInt)
        assertNull(decoder.decode(legacyJsonFrame(2, "{\"models\":[]}")))
    }

    @Test
    fun `clear resets the protocol handshake`() {
        val decoder = PacketCodecTest.acceptedDecoder()
        val frame = LanternProtocol.encodeS2C(7, JsonObject().also { it.addProperty("key", "k") })
        assertNotNull(decoder.decode(frame))

        decoder.clear()

        assertNull(decoder.decode(frame))
    }

    @Test
    fun `large repetitive bodies are compressed`() {
        val payload = PacketSamples.uiScreens(10)
        val body = LanternProtocol.encodeBody(5, payload, null)
        val frame = LanternProtocol.encodeS2C(5, payload)

        assertEquals(5 or LanternProtocol.COMPRESSED_FLAG, frame[0].toInt() and 0xFF)
        assertTrue(frame.size < body.size, "compressed ${frame.size} B vs body ${body.size} B")
        assertEquals(PacketCodecTest.viaJson(payload).toString(), PacketCodecTest.roundTrip(5, payload).toString())
    }

    @Test
    fun `compressed bodies must match the declared length`() {
        val body = LanternProtocol.encodeBody(5, PacketSamples.uiScreens(2), null)
        val compressed = deflate(body)

        assertNotNull(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size, compressed)))
        assertNull(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size - 1, compressed)))
        assertNull(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size + 1, compressed)))
        assertNull(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, Int.MAX_VALUE, compressed)))
        assertNull(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size, compressed.copyOf(compressed.size / 2))))
    }

    @Test
    fun `oversized frames are chunked and reassembled out of order`() {
        val payload = incompressiblePayload()
        val frame = LanternProtocol.encodeS2C(5, payload)
        val chunks = LanternProtocol.splitIntoChunks(frame, 7)
        assertTrue(chunks.size >= 2, "expected chunks, frame is ${frame.size} B")
        chunks.forEach { assertTrue(it.size <= LanternProtocol.MAX_FRAME_BYTES) }

        val decoder = PacketCodecTest.acceptedDecoder()
        val order = chunks.indices.reversed()
        val decoded = order.mapNotNull { decoder.decode(chunks[it]) }

        assertEquals(1, decoded.size)
        assertEquals(PacketCodecTest.viaJson(payload).toString(), decoded.single().payload.toString())
    }

    @Test
    fun `duplicate chunks are ignored`() {
        val chunks = LanternProtocol.splitIntoChunks(LanternProtocol.encodeS2C(5, incompressiblePayload()), 3)
        val decoder = PacketCodecTest.acceptedDecoder()

        assertNull(decoder.decode(chunks[0]))
        assertNull(decoder.decode(chunks[0]))
        val decoded = chunks.drop(1).mapNotNull { decoder.decode(it) }

        assertEquals(1, decoded.size)
    }

    @Test
    fun `clear discards incomplete messages`() {
        val chunks = LanternProtocol.splitIntoChunks(LanternProtocol.encodeS2C(5, incompressiblePayload()), 9)
        val decoder = PacketCodecTest.acceptedDecoder()

        assertNull(decoder.decode(chunks[0]))
        decoder.clear()
        decoder.decode(LanternProtocol.encodeProtocolInfo())

        assertTrue(chunks.drop(1).mapNotNull { decoder.decode(it) }.isEmpty())
    }

    @Test
    fun `malformed frames are dropped without throwing`() {
        val decoder = PacketCodecTest.acceptedDecoder()
        val good = LanternProtocol.encodeS2C(15, PacketSamples.animationControl())

        assertNull(decoder.decode(good.copyOf(good.size - 3)))
        assertNull(decoder.decode(good + byteArrayOf(0)))
        // 有无标记里多了字段表之外的位
        assertNull(decoder.decode(byteArrayOf(99, 0x04)))
        // 布尔字节只能是 0 / 1
        assertNull(decoder.decode(byteArrayOf(99, 0x02, 0x07)))
        // 通用编码里的未知标签
        assertNull(decoder.decode(byteArrayOf(5, 1, 0, 1, 'a'.code.toByte(), 99)))
        // 字符串引用越界
        assertNull(decoder.decode(byteArrayOf(5, 1, 3, BinaryJson.TRUE.toByte())))
        // 元素个数大于剩余字节
        assertNull(decoder.decode(byteArrayOf(5, 100)))
        // 分片帧里装的是分片 / 协议版本
        assertNull(decoder.decode(byteArrayOf(0, 1, 1, 0, 0x7F, 2)))
        assertNotNull(decoder.decode(good))
    }

    @Test
    fun `keyboard payload keeps Bukkit wire format`() {
        val encoded = LanternProtocol.encodeKeyboardC2S("ctrl+k", true, true)

        assertEquals(KeyboardC2SPacket("ctrl+k", true, true), LanternProtocol.decodeKeyboardC2S(encoded))
    }

    @Test
    fun `keyboard decoder rejects oversized lengths`() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(LanternProtocol.C2S_KEYBOARD_PACKET_TYPE)
            data.writeInt(Int.MAX_VALUE)
            data.writeBoolean(true)
        }

        assertNull(LanternProtocol.decodeKeyboardC2S(output.toByteArray()))
    }

    @Test
    fun `animation event round trips`() {
        val encoded = LanternProtocol.encodeAnimationEventC2S("5f1c3a2e-8b4d-4c1a-9e2f-0a1b2c3d4e5f", "翻滚", "request:forward_left")

        assertEquals(
            AnimationEventC2SPacket("5f1c3a2e-8b4d-4c1a-9e2f-0a1b2c3d4e5f", "翻滚", "request:forward_left"),
            LanternProtocol.decodeAnimationEventC2S(encoded)
        )
    }

    private fun incompressiblePayload(): JsonObject {
        val random = Random(42)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val array = JsonArray()
        repeat(2_000) {
            array.add(String(CharArray(40) { alphabet[random.nextInt(alphabet.length)] }))
        }
        return JsonObject().also { it.add("noise", array) }
    }

    private fun legacyJsonFrame(packetId: Int, json: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(0)
            data.writeInt(packetId)
            data.write(json.toByteArray(Charsets.UTF_8))
        }
        return output.toByteArray()
    }

    private fun compressedFrame(packetId: Int, declaredLength: Int, compressed: ByteArray): ByteArray {
        val writer = PacketWriter()
        writer.writeByte(packetId or LanternProtocol.COMPRESSED_FLAG)
        writer.writeVarInt(declaredLength)
        writer.writeBytes(compressed)
        return writer.toByteArray()
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return output.toByteArray()
    }
}
