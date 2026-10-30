package org.lantern.core.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Random
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `data before the protocol info packet is delivered right after it`() {
        val decoder = MainPayloadDecoder()
        val frame = keyFrame("k")

        assertTrue(decoder.decode(frame).isEmpty())
        val packets = decoder.decode(LanternProtocol.encodeProtocolInfo())

        assertEquals(listOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID, 7), packets.map { it.packetId })
        assertEquals(LanternProtocol.PROTOCOL_VERSION, packets[0].payload.get("version").asInt)
        assertEquals(1, packets[0].payload.get("held").asInt)
        assertEquals("k", packets[1].payload.get("key").asString)
        assertEquals("k", decoder.decode(frame).single().payload.get("key").asString)
    }

    @Test
    fun `a different protocol version is reported and discards held and later data`() {
        val decoder = MainPayloadDecoder()

        assertTrue(decoder.decode(keyFrame("k")).isEmpty())
        val packets = decoder.decode(byteArrayOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID.toByte(), 3))

        assertEquals(3, packets.single().payload.get("version").asInt)
        assertTrue(decoder.decode(keyFrame("k")).isEmpty())
    }

    @Test
    fun `legacy JSON frames are reported once as version 1`() {
        val decoder = MainPayloadDecoder()
        val legacy = legacyJsonFrame(1, "{\"characters\":[]}")

        assertEquals(LanternProtocol.LEGACY_JSON_PROTOCOL_VERSION, decoder.decode(legacy).single().payload.get("version").asInt)
        assertTrue(decoder.decode(legacyJsonFrame(2, "{\"models\":[]}")).isEmpty())
    }

    @Test
    fun `a legacy JSON frame discards held data`() {
        val decoder = MainPayloadDecoder()
        decoder.decode(keyFrame("k"))
        decoder.decode(legacyJsonFrame(1, "{\"characters\":[]}"))

        val packets = decoder.decode(LanternProtocol.encodeProtocolInfo())

        assertEquals(listOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID), packets.map { it.packetId })
        assertNull(packets[0].payload.get("held"))
    }

    @Test
    fun `clear resets the protocol handshake`() {
        val decoder = PacketCodecTest.acceptedDecoder()
        assertEquals(1, decoder.decode(keyFrame("k")).size)

        decoder.clear()

        assertTrue(decoder.decode(keyFrame("k")).isEmpty())
        assertEquals(
            listOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID, 7),
            decoder.decode(LanternProtocol.encodeProtocolInfo()).map { it.packetId }
        )
    }

    @Test
    fun `clear drops held data`() {
        val decoder = MainPayloadDecoder()
        decoder.decode(keyFrame("k"))

        decoder.clear()

        assertEquals(
            listOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID),
            decoder.decode(LanternProtocol.encodeProtocolInfo()).map { it.packetId }
        )
    }

    @Test
    fun `frames beyond the hold limit are dropped and counted`() {
        val decoder = MainPayloadDecoder()
        val limit = 4096
        repeat(limit + 3) { decoder.decode(keyFrame("k$it")) }

        val packets = decoder.decode(LanternProtocol.encodeProtocolInfo())

        assertEquals(limit, packets[0].payload.get("held").asInt)
        assertEquals(3, packets[0].payload.get("overflow").asInt)
        assertEquals((0 until limit).map { "k$it" }, packets.drop(1).map { it.payload.get("key").asString })
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

        assertEquals(1, PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size, compressed)).size)
        assertTrue(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size - 1, compressed)).isEmpty())
        assertTrue(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size + 1, compressed)).isEmpty())
        assertTrue(PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, Int.MAX_VALUE, compressed)).isEmpty())
        assertTrue(
            PacketCodecTest.acceptedDecoder().decode(compressedFrame(5, body.size, compressed.copyOf(compressed.size / 2))).isEmpty()
        )
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
        val decoded = order.flatMap { decoder.decode(chunks[it]) }

        assertEquals(1, decoded.size)
        assertEquals(PacketCodecTest.viaJson(payload).toString(), decoded.single().payload.toString())
    }

    @Test
    fun `duplicate chunks are ignored`() {
        val chunks = LanternProtocol.splitIntoChunks(LanternProtocol.encodeS2C(5, incompressiblePayload()), 3)
        val decoder = PacketCodecTest.acceptedDecoder()

        assertTrue(decoder.decode(chunks[0]).isEmpty())
        assertTrue(decoder.decode(chunks[0]).isEmpty())
        val decoded = chunks.drop(1).flatMap { decoder.decode(it) }

        assertEquals(1, decoded.size)
    }

    @Test
    fun `clear discards incomplete messages`() {
        val chunks = LanternProtocol.splitIntoChunks(LanternProtocol.encodeS2C(5, incompressiblePayload()), 9)
        val decoder = PacketCodecTest.acceptedDecoder()

        assertTrue(decoder.decode(chunks[0]).isEmpty())
        decoder.clear()
        decoder.decode(LanternProtocol.encodeProtocolInfo())

        assertTrue(chunks.drop(1).flatMap { decoder.decode(it) }.isEmpty())
    }

    @Test
    fun `chunks that arrive before the protocol info packet are reassembled after it`() {
        val payload = incompressiblePayload()
        val chunks = LanternProtocol.splitIntoChunks(LanternProtocol.encodeS2C(5, payload), 11)
        val decoder = MainPayloadDecoder()

        assertTrue(decoder.decode(chunks[0]).isEmpty())
        assertEquals(
            listOf(LanternProtocol.PROTOCOL_INFO_PACKET_ID),
            decoder.decode(LanternProtocol.encodeProtocolInfo()).map { it.packetId }
        )
        val decoded = chunks.drop(1).flatMap { decoder.decode(it) }

        assertEquals(PacketCodecTest.viaJson(payload).toString(), decoded.single().payload.toString())
    }

    @Test
    fun `a reordered join burst is delivered in full wherever the protocol info packet lands`() {
        val samples = PacketSamples.all()
        // NetworkHandler.sendPackets 的发送顺序，外加一个要分片的大包
        val burst = listOf(7, 1, 2, 3, 18, 14, 4, 5, 8, 9, 10, 19, 22, 11)
            .map { id -> id to samples.first { it.first == id }.second } + (5 to incompressiblePayload())
        val dataFrames = burst.flatMapIndexed { index, (packetId, payload) ->
            LanternProtocol.splitIntoChunks(LanternProtocol.encodeS2C(packetId, payload), index + 1)
        }
        assertTrue(dataFrames.size > burst.size, "expected a chunked message in the burst")
        val expected = burst
            .map { (packetId, payload) -> "$packetId ${PacketCodecTest.canonical(PacketCodecTest.viaJson(payload))}" }
            .sorted()

        repeat(50) { seed ->
            val infoFrame = LanternProtocol.encodeProtocolInfo()
            val frames = (dataFrames + listOf(infoFrame)).shuffled(Random(seed.toLong()))
            val decoder = MainPayloadDecoder()

            val packets = frames.flatMap { decoder.decode(it) }

            val info = packets.single { it.packetId == LanternProtocol.PROTOCOL_INFO_PACKET_ID }
            assertEquals(frames.indexOf(infoFrame), info.payload.get("held")?.asInt ?: 0, "seed $seed")
            val delivered = packets
                .filter { it.packetId != LanternProtocol.PROTOCOL_INFO_PACKET_ID }
                .map { "${it.packetId} ${PacketCodecTest.canonical(it.payload)}" }
                .sorted()
            assertEquals(expected, delivered, "seed $seed")
        }
    }

    @Test
    fun `malformed frames are dropped without throwing`() {
        val decoder = PacketCodecTest.acceptedDecoder()
        val good = LanternProtocol.encodeS2C(15, PacketSamples.animationControl())

        assertTrue(decoder.decode(good.copyOf(good.size - 3)).isEmpty())
        assertTrue(decoder.decode(good + byteArrayOf(0)).isEmpty())
        // 有无标记里多了字段表之外的位
        assertTrue(decoder.decode(byteArrayOf(99, 0x04)).isEmpty())
        // 布尔字节只能是 0 / 1
        assertTrue(decoder.decode(byteArrayOf(99, 0x02, 0x07)).isEmpty())
        // 通用编码里的未知标签
        assertTrue(decoder.decode(byteArrayOf(5, 1, 0, 1, 'a'.code.toByte(), 99)).isEmpty())
        // 字符串引用越界
        assertTrue(decoder.decode(byteArrayOf(5, 1, 3, BinaryJson.TRUE.toByte())).isEmpty())
        // 元素个数大于剩余字节
        assertTrue(decoder.decode(byteArrayOf(5, 100)).isEmpty())
        // 分片帧里装的是分片 / 协议版本
        assertTrue(decoder.decode(byteArrayOf(0, 1, 1, 0, 0x7F, 2)).isEmpty())
        assertEquals(1, decoder.decode(good).size)
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

    private fun keyFrame(key: String): ByteArray =
        LanternProtocol.encodeS2C(7, JsonObject().also { it.addProperty("key", key) })

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
