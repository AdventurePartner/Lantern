package org.lantern.core.protocol

import com.google.gson.JsonObject
import org.lantern.core.CoreLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.LinkedHashMap
import java.util.zip.Deflater
import java.util.zip.Inflater

data class MainS2CPacket(
    val packetId: Int,
    val payload: JsonObject
)

data class KeyboardC2SPacket(
    val key: String,
    val press: Boolean,
    val inGui: Boolean
)

data class AnimationEventC2SPacket(
    val uuid: String,
    val animation: String,
    val event: String
)

/**
 * 客户端 lantern:main 解码器，每个连接一份（断线时 [clear]）。
 *
 * 收到同版本的协议版本包之前丢弃一切数据：字段表按位置编码，版本不一致时
 * 解出来的不是报错就是错位的值。协议版本包本身（以及识别出的旧版 JSON 帧）
 * 作为 [LanternProtocol.PROTOCOL_INFO_PACKET_ID] 交给上层提示玩家
 */
class MainPayloadDecoder {
    private class PendingMessage(val totalChunks: Int) {
        val chunks = arrayOfNulls<ByteArray>(totalChunks)
        var receivedChunks = 0
        var receivedBytes = 0
    }

    private val pendingMessages = LinkedHashMap<Int, PendingMessage>()
    private var accepted = false
    private var legacyReported = false
    private val reportedFailures = HashSet<Int>()

    fun decode(data: ByteArray): MainS2CPacket? {
        if (data.isEmpty()) return null
        val head = data[0].toInt() and 0xFF
        return try {
            when {
                head == LanternProtocol.CHUNK_FRAME_HEAD && isLegacyJsonFrame(data) -> reportLegacy()
                head == LanternProtocol.CHUNK_FRAME_HEAD -> {
                    if (!accepted) return null
                    decodeChunk(data)?.let { decodeFrame(it, fromChunks = true) }
                }
                else -> decodeFrame(data, fromChunks = false)
            }
        } catch (e: Exception) {
            if (reportedFailures.add(head and LanternProtocol.PACKET_ID_MASK)) {
                CoreLog.logger.warning(
                    "lantern:main packet ${head and LanternProtocol.PACKET_ID_MASK} failed to decode and was dropped: ${e.message}"
                )
            }
            null
        }
    }

    fun clear() {
        pendingMessages.clear()
        accepted = false
        legacyReported = false
    }

    private fun decodeFrame(frame: ByteArray, fromChunks: Boolean): MainS2CPacket? {
        val head = frame[0].toInt() and 0xFF
        val packetId = head and LanternProtocol.PACKET_ID_MASK
        if (head == LanternProtocol.PROTOCOL_INFO_PACKET_ID && !fromChunks) {
            val reader = PacketReader(frame, 1)
            val version = reader.readVarInt()
            reader.expectEnd()
            accepted = version == LanternProtocol.PROTOCOL_VERSION
            return LanternProtocol.protocolInfoPacket(version)
        }
        if (!accepted) return null
        if (packetId == LanternProtocol.CHUNK_FRAME_HEAD || packetId == LanternProtocol.PROTOCOL_INFO_PACKET_ID) {
            throw MalformedPacketException("frame head $head is not a data packet")
        }

        val reader = PacketReader(frame, 1)
        val body = if (head and LanternProtocol.COMPRESSED_FLAG != 0) {
            val expectedLength = reader.readVarInt()
            LanternProtocol.inflate(reader.readRemaining(), expectedLength)
        } else {
            reader.readRemaining()
        }
        return MainS2CPacket(packetId, LanternProtocol.decodeBody(packetId, body))
    }

    private fun decodeChunk(data: ByteArray): ByteArray? {
        val reader = PacketReader(data, 1)
        val messageId = reader.readVarInt()
        val totalChunks = reader.readVarInt()
        val chunkIndex = reader.readVarInt()
        val chunk = reader.readRemaining()

        if (
            totalChunks !in 1..MAX_CHUNKS ||
            chunkIndex !in 0 until totalChunks ||
            chunk.isEmpty() || chunk.size > MAX_CHUNK_BYTES
        ) {
            return null
        }

        var pending = pendingMessages[messageId]
        if (pending == null) {
            if (pendingMessages.size >= MAX_PENDING_MESSAGES) return null
            pending = PendingMessage(totalChunks)
            pendingMessages[messageId] = pending
        } else if (pending.totalChunks != totalChunks) {
            pendingMessages.remove(messageId)
            return null
        }

        if (pending.chunks[chunkIndex] != null) return null
        if (pending.receivedBytes.toLong() + chunk.size > MAX_MESSAGE_BYTES) {
            pendingMessages.remove(messageId)
            return null
        }

        pending.chunks[chunkIndex] = chunk
        pending.receivedChunks++
        pending.receivedBytes += chunk.size
        if (pending.receivedChunks != totalChunks) return null

        pendingMessages.remove(messageId)
        val assembled = ByteArray(pending.receivedBytes)
        var offset = 0
        pending.chunks.forEach { stored ->
            stored!!.copyInto(assembled, offset)
            offset += stored.size
        }
        return assembled
    }

    /** 旧版 JSON 帧：type 0 + int packetId，packetId 都小于 256，所以前四个字节全是 0 */
    private fun isLegacyJsonFrame(data: ByteArray): Boolean =
        !accepted && data.size >= 5 && data[1].toInt() == 0 && data[2].toInt() == 0 && data[3].toInt() == 0

    private fun reportLegacy(): MainS2CPacket? {
        if (legacyReported) return null
        legacyReported = true
        return LanternProtocol.protocolInfoPacket(LanternProtocol.LEGACY_JSON_PROTOCOL_VERSION)
    }

    private companion object {
        const val MAX_PENDING_MESSAGES = 128
        const val MAX_CHUNKS = 4096
        const val MAX_CHUNK_BYTES = 32766
        const val MAX_MESSAGE_BYTES = LanternProtocol.MAX_MESSAGE_BYTES
    }
}

/**
 * lantern:main 的线上格式。
 *
 * S2C 帧的首字节：
 *   0x01..0x7E  packetId，后面直接是包体
 *   | 0x80      最高位 = 包体经 zlib 压缩，先写 varint 解压后长度
 *   0x00        分片：varint 消息号 + varint 总片数 + varint 片序号 + 片数据，拼完是一个普通帧
 *   0x7F        协议版本：varint 版本号，格式永远不变
 * 包体：有字段表的包按 [PacketSchemas] 逐字段写，其余整包走 [BinaryJson]。
 *
 * C2S 帧沿用原格式：1 键盘、3 动画事件
 */
object LanternProtocol {
    const val CHANNEL_MAIN = "lantern:main"

    /** 线上格式或 [PacketSchemas] 有任何改动都要加一 */
    const val PROTOCOL_VERSION = 2
    /** 第一版：type 0 + int packetId + JSON 文本 */
    const val LEGACY_JSON_PROTOCOL_VERSION = 1

    const val CHUNK_FRAME_HEAD = 0x00
    const val PROTOCOL_INFO_PACKET_ID = 0x7F
    const val COMPRESSED_FLAG = 0x80
    const val PACKET_ID_MASK = 0x7F

    const val C2S_KEYBOARD_PACKET_TYPE = 1
    const val C2S_ANIMATION_EVENT_PACKET_TYPE = 3

    /** 超过这个大小的帧要分片（Bukkit 插件消息上限） */
    const val MAX_FRAME_BYTES = 32766
    const val MAX_CHUNK_PAYLOAD_BYTES = 32_000
    const val MAX_MESSAGE_BYTES = 64 * 1024 * 1024
    /** 包体达到这个大小才尝试压缩 */
    const val COMPRESSION_THRESHOLD = 256

    /**
     * 编码一个 S2C 包。[onSchemaMiss] 在字段落进附加字段区时回调字段路径，
     * 服务端用它提示字段表该补了
     */
    fun encodeS2C(packetId: Int, payload: JsonObject, onSchemaMiss: ((String) -> Unit)? = null): ByteArray {
        require(packetId in 1 until PROTOCOL_INFO_PACKET_ID) { "packetId $packetId out of range 1..126" }
        val body = encodeBody(packetId, payload, onSchemaMiss)
        if (body.size >= COMPRESSION_THRESHOLD) {
            val compressed = deflate(body)
            val header = PacketWriter(8)
            header.writeByte(packetId or COMPRESSED_FLAG)
            header.writeVarInt(body.size)
            if (header.size + compressed.size < body.size + 1) {
                header.writeBytes(compressed)
                return header.toByteArray()
            }
        }
        val frame = PacketWriter(body.size + 1)
        frame.writeByte(packetId)
        frame.writeBytes(body)
        return frame.toByteArray()
    }

    fun encodeProtocolInfo(): ByteArray {
        val writer = PacketWriter(8)
        writer.writeByte(PROTOCOL_INFO_PACKET_ID)
        writer.writeVarInt(PROTOCOL_VERSION)
        return writer.toByteArray()
    }

    /** 帧不超过 [MAX_FRAME_BYTES] 时原样返回；否则切成分片帧 */
    fun splitIntoChunks(frame: ByteArray, messageId: Int): List<ByteArray> {
        if (frame.size <= MAX_FRAME_BYTES) return listOf(frame)
        val totalChunks = (frame.size + MAX_CHUNK_PAYLOAD_BYTES - 1) / MAX_CHUNK_PAYLOAD_BYTES
        return (0 until totalChunks).map { chunkIndex ->
            val offset = chunkIndex * MAX_CHUNK_PAYLOAD_BYTES
            val length = (frame.size - offset).coerceAtMost(MAX_CHUNK_PAYLOAD_BYTES)
            val writer = PacketWriter(length + 16)
            writer.writeByte(CHUNK_FRAME_HEAD)
            writer.writeVarInt(messageId)
            writer.writeVarInt(totalChunks)
            writer.writeVarInt(chunkIndex)
            writer.writeBytes(frame, offset, length)
            writer.toByteArray()
        }
    }

    internal fun encodeBody(packetId: Int, payload: JsonObject, onSchemaMiss: ((String) -> Unit)?): ByteArray {
        val ctx = EncodeContext(PacketWriter(), onSchemaMiss)
        val schema = PacketSchemas.of(packetId)
        if (schema != null) {
            RecordCodec.write(ctx, schema, payload, "", 0)
        } else {
            BinaryJson.writeObjectBody(ctx, payload, 0)
        }
        return ctx.writer.toByteArray()
    }

    internal fun decodeBody(packetId: Int, body: ByteArray): JsonObject {
        val ctx = DecodeContext(PacketReader(body))
        val schema = PacketSchemas.of(packetId)
        val payload = if (schema != null) {
            RecordCodec.read(ctx, schema, 0)
        } else {
            BinaryJson.readObjectBody(ctx, 0)
        }
        ctx.reader.expectEnd()
        return payload
    }

    internal fun protocolInfoPacket(version: Int): MainS2CPacket {
        val payload = JsonObject()
        payload.addProperty("version", version)
        return MainS2CPacket(PROTOCOL_INFO_PACKET_ID, payload)
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream(input.size / 2 + 64)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** 解压到恰好 [expectedLength] 字节；数据比声明的短或长都拒绝（防压缩炸弹） */
    internal fun inflate(input: ByteArray, expectedLength: Int): ByteArray {
        if (expectedLength !in 1..MAX_MESSAGE_BYTES) {
            throw MalformedPacketException("declared length $expectedLength out of range")
        }
        val inflater = Inflater()
        try {
            inflater.setInput(input)
            val output = ByteArray(expectedLength)
            var written = 0
            while (written < expectedLength) {
                val count = inflater.inflate(output, written, expectedLength - written)
                if (count == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
                    throw MalformedPacketException("compressed body shorter than declared $expectedLength bytes")
                }
                written += count
            }
            if (!inflater.finished() && inflater.inflate(ByteArray(1)) != 0) {
                throw MalformedPacketException("compressed body longer than declared $expectedLength bytes")
            }
            if (!inflater.finished() || inflater.remaining != 0) {
                throw MalformedPacketException("compressed body has trailing data")
            }
            return output
        } finally {
            inflater.end()
        }
    }

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

    fun encodeAnimationEventC2S(uuid: String, animation: String, event: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(C2S_ANIMATION_EVENT_PACKET_TYPE)
            data.writeUTF(uuid)
            data.writeUTF(animation)
            data.writeUTF(event)
        }
        return output.toByteArray()
    }

    fun decodeAnimationEventC2S(data: ByteArray): AnimationEventC2SPacket? {
        if (data.isEmpty()) return null
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readByte().toInt() != C2S_ANIMATION_EVENT_PACKET_TYPE) return null
                val uuid = input.readUTF()
                if (uuid.length !in 1..64) return null
                val animation = input.readUTF()
                if (animation.length !in 1..256) return null
                val event = input.readUTF()
                if (event.length !in 1..32) return null
                AnimationEventC2SPacket(uuid, animation, event)
            }
        } catch (_: Exception) {
            null
        }
    }
}
