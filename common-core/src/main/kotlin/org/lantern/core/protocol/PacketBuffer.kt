package org.lantern.core.protocol

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.UUID

/** 包体字节不合法（截断、越界下标、未知标签等）。MainPayloadDecoder 捕获后丢弃整包 */
class MalformedPacketException(message: String) : RuntimeException(message)

/** 按需扩容的大端写缓冲 */
class PacketWriter(initialCapacity: Int = 256) {
    private var buffer: ByteBuffer = ByteBuffer.allocate(initialCapacity.coerceAtLeast(16))

    val size: Int get() = buffer.position()

    private fun ensure(extra: Int) {
        if (buffer.remaining() >= extra) return
        val required = buffer.position().toLong() + extra
        var capacity = buffer.capacity().toLong() * 2
        while (capacity < required) capacity *= 2
        require(capacity <= Int.MAX_VALUE) { "packet too large: $required bytes" }
        val grown = ByteBuffer.allocate(capacity.toInt())
        grown.put(buffer.array(), 0, buffer.position())
        buffer = grown
    }

    fun writeByte(value: Int) {
        ensure(1)
        buffer.put(value.toByte())
    }

    fun writeBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        ensure(length)
        buffer.put(bytes, offset, length)
    }

    /** 无符号 LEB128：负数按 32 位无符号写满 5 字节 */
    fun writeVarInt(value: Int) {
        var remaining = value
        while (remaining and 0x7F.inv() != 0) {
            writeByte((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        writeByte(remaining)
    }

    fun writeVarLong(value: Long) {
        var remaining = value
        while (remaining and 0x7FL.inv() != 0L) {
            writeByte(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        writeByte(remaining.toInt())
    }

    /** zigzag 变长：小的负数也只占一两个字节 */
    fun writeSignedVarLong(value: Long) {
        writeVarLong((value shl 1) xor (value shr 63))
    }

    fun writeFloat(value: Float) {
        ensure(4)
        buffer.putFloat(value)
    }

    fun writeDouble(value: Double) {
        ensure(8)
        buffer.putDouble(value)
    }

    fun writeUuid(value: UUID) {
        ensure(16)
        buffer.putLong(value.mostSignificantBits)
        buffer.putLong(value.leastSignificantBits)
    }

    fun toByteArray(): ByteArray = buffer.array().copyOf(buffer.position())
}

/** 带边界检查的大端读缓冲：任何越界或不合法的编码都抛 [MalformedPacketException] */
class PacketReader(private val buffer: ByteBuffer) {
    constructor(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) :
        this(ByteBuffer.wrap(bytes, offset, length))

    val remaining: Int get() = buffer.remaining()

    fun readByte(): Int {
        if (!buffer.hasRemaining()) throw MalformedPacketException("unexpected end of packet")
        return buffer.get().toInt() and 0xFF
    }

    fun readBytes(length: Int): ByteArray {
        if (length < 0 || length > buffer.remaining()) {
            throw MalformedPacketException("byte length $length exceeds remaining ${buffer.remaining()}")
        }
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }

    fun readRemaining(): ByteArray = readBytes(buffer.remaining())

    fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val byte = readByte()
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift >= 35) throw MalformedPacketException("varint too long")
        }
    }

    fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = readByte()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift >= 70) throw MalformedPacketException("varlong too long")
        }
    }

    fun readSignedVarLong(): Long {
        val raw = readVarLong()
        return (raw ushr 1) xor -(raw and 1L)
    }

    /** 读一个元素个数。每个元素至少占一个字节，所以个数不可能超过剩余字节数 */
    fun readCount(): Int {
        val count = readVarInt()
        if (count < 0 || count > buffer.remaining()) {
            throw MalformedPacketException("element count $count exceeds remaining ${buffer.remaining()}")
        }
        return count
    }

    fun readFloat(): Float = guard(4) { buffer.float }

    fun readDouble(): Double = guard(8) { buffer.double }

    fun readUuid(): UUID = guard(16) { UUID(buffer.long, buffer.long) }

    fun expectEnd() {
        if (buffer.hasRemaining()) throw MalformedPacketException("${buffer.remaining()} trailing bytes")
    }

    private inline fun <T> guard(bytes: Int, read: () -> T): T {
        if (buffer.remaining() < bytes) throw MalformedPacketException("unexpected end of packet")
        return try {
            read()
        } catch (e: BufferUnderflowException) {
            throw MalformedPacketException("unexpected end of packet")
        }
    }
}
