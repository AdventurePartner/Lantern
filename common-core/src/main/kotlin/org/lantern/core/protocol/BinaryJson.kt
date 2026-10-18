package org.lantern.core.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber
import java.math.BigDecimal
import java.util.UUID

/**
 * 通用值编码：带类型标签的二进制 Gson 树，给没有字段表的包和字段表里的 tree 字段用。
 *
 * 解码结果要和「服务端 Gson.toJson → 客户端 Gson 解析」得到的树行为一致：
 * 编码时按原来的 Java 类型选标签（Integer / Float / Double 的 toString 各不相同），
 * 对象里值为 JsonNull 的成员丢弃（Gson 默认不序列化 null 成员），数组里的 null 保留。
 */
internal object BinaryJson {
    const val NULL = 0
    const val FALSE = 1
    const val TRUE = 2
    /** zigzag varlong，解码为 Int（放得下时）或 Long */
    const val INT = 3
    const val FLOAT = 4
    const val DOUBLE = 5
    /** 整数值的 Double（1.0、-3.0），zigzag varlong，解码回 Double */
    const val DOUBLE_INT = 6
    /** float 能精确表示的 Double（0.5、2.25），4 字节，解码回 Double */
    const val DOUBLE_FLOAT = 7
    /** BigDecimal、LazilyParsedNumber 等其他 Number：原文，解码为 LazilyParsedNumber */
    const val NUMBER_TEXT = 8
    const val STRING = 9
    /** 规范小写形式的 UUID 字符串，16 字节 */
    const val UUID_STRING = 10
    const val ARRAY = 11
    const val OBJECT = 12

    const val MAX_DEPTH = 256

    fun writeValue(ctx: EncodeContext, element: JsonElement, depth: Int) {
        checkDepth(depth)
        val writer = ctx.writer
        when {
            element.isJsonNull -> writer.writeByte(NULL)
            element.isJsonPrimitive -> writePrimitive(ctx, element.asJsonPrimitive)
            element.isJsonArray -> {
                val array = element.asJsonArray
                writer.writeByte(ARRAY)
                writer.writeVarInt(array.size())
                array.forEach { writeValue(ctx, it, depth + 1) }
            }
            else -> {
                writer.writeByte(OBJECT)
                writeObjectBody(ctx, element.asJsonObject, depth + 1)
            }
        }
    }

    fun writeObjectBody(ctx: EncodeContext, obj: JsonObject, depth: Int) {
        checkDepth(depth)
        val members = obj.entrySet().filter { !it.value.isJsonNull }
        ctx.writer.writeVarInt(members.size)
        members.forEach { (key, value) ->
            ctx.strings.write(ctx.writer, key)
            writeValue(ctx, value, depth + 1)
        }
    }

    private fun writePrimitive(ctx: EncodeContext, primitive: JsonPrimitive) {
        val writer = ctx.writer
        when {
            primitive.isBoolean -> writer.writeByte(if (primitive.asBoolean) TRUE else FALSE)
            primitive.isNumber -> writeNumber(ctx, primitive.asNumber)
            else -> {
                val text = primitive.asString
                val uuid = parseCanonicalUuid(text)
                if (uuid != null) {
                    writer.writeByte(UUID_STRING)
                    writer.writeUuid(uuid)
                } else {
                    writer.writeByte(STRING)
                    ctx.strings.write(writer, text)
                }
            }
        }
    }

    fun writeNumber(ctx: EncodeContext, number: Number) {
        val writer = ctx.writer
        when (number) {
            is Int, is Long, is Short, is Byte -> {
                writer.writeByte(INT)
                writer.writeSignedVarLong(number.toLong())
            }
            is Float -> {
                writer.writeByte(FLOAT)
                writer.writeFloat(number)
            }
            is Double -> writeDouble(writer, number)
            else -> {
                writer.writeByte(NUMBER_TEXT)
                ctx.strings.write(writer, number.toString())
            }
        }
    }

    private fun writeDouble(writer: PacketWriter, value: Double) {
        val asLong = value.toLong()
        // -0.0 转成 long 会丢符号，走 float 分支保留
        val negativeZero = value == 0.0 && 1.0 / value < 0
        when {
            !negativeZero && asLong.toDouble() == value -> {
                writer.writeByte(DOUBLE_INT)
                writer.writeSignedVarLong(asLong)
            }
            value.toFloat().toDouble() == value -> {
                writer.writeByte(DOUBLE_FLOAT)
                writer.writeFloat(value.toFloat())
            }
            else -> {
                writer.writeByte(DOUBLE)
                writer.writeDouble(value)
            }
        }
    }

    fun readValue(ctx: DecodeContext, depth: Int): JsonElement {
        checkDepth(depth)
        val reader = ctx.reader
        return when (val tag = reader.readByte()) {
            NULL -> JsonNull.INSTANCE
            FALSE -> JsonPrimitive(false)
            TRUE -> JsonPrimitive(true)
            STRING -> JsonPrimitive(ctx.strings.read(reader))
            UUID_STRING -> JsonPrimitive(reader.readUuid().toString())
            ARRAY -> {
                val array = JsonArray()
                repeat(reader.readCount()) { array.add(readValue(ctx, depth + 1)) }
                array
            }
            OBJECT -> readObjectBody(ctx, depth + 1)
            else -> readNumber(ctx, tag)
        }
    }

    fun readObjectBody(ctx: DecodeContext, depth: Int): JsonObject {
        checkDepth(depth)
        val obj = JsonObject()
        repeat(ctx.reader.readCount()) {
            val key = ctx.strings.read(ctx.reader)
            obj.add(key, readValue(ctx, depth + 1))
        }
        return obj
    }

    /** 读一个数字标签之后的值；标签不是数字类型时抛 [MalformedPacketException] */
    fun readNumber(ctx: DecodeContext, tag: Int): JsonPrimitive {
        val reader = ctx.reader
        return when (tag) {
            INT -> numberOf(reader.readSignedVarLong().toString())
            FLOAT -> numberOf(reader.readFloat().toString())
            DOUBLE -> numberOf(reader.readDouble().toString())
            DOUBLE_INT -> numberOf(reader.readSignedVarLong().toDouble().toString())
            DOUBLE_FLOAT -> numberOf(reader.readFloat().toDouble().toString())
            NUMBER_TEXT -> {
                val text = ctx.strings.read(reader)
                try {
                    BigDecimal(text)
                } catch (_: NumberFormatException) {
                    throw MalformedPacketException("invalid number text '$text'")
                }
                numberOf(text)
            }
            else -> throw MalformedPacketException("unknown value tag $tag")
        }
    }

    /**
     * 文本是服务端那个 Number 的 toString()。JSON 时代客户端拿到的就是
     * LazilyParsedNumber(这段文本)，asFloat / asDouble / asString 的结果逐位一致
     * （服务端发 Float 0.1f，asDouble 仍是 0.1 而不是 0.10000000149011612）
     */
    fun numberOf(text: String): JsonPrimitive = JsonPrimitive(LazilyParsedNumber(text))

    /** 只认 8-4-4-4-12 的小写十六进制：这种写法 UUID.toString() 能原样还原 */
    fun parseCanonicalUuid(text: String): UUID? {
        if (text.length != 36) return null
        for (i in text.indices) {
            val c = text[i]
            val ok = if (i == 8 || i == 13 || i == 18 || i == 23) c == '-' else c in '0'..'9' || c in 'a'..'f'
            if (!ok) return null
        }
        return UUID.fromString(text)
    }

    private fun checkDepth(depth: Int) {
        if (depth > MAX_DEPTH) throw MalformedPacketException("nesting deeper than $MAX_DEPTH")
    }
}

/**
 * 包内字符串表：第一次出现写 0 + 长度 + UTF-8 并登记，之后只写 varint(下标 + 1)。
 * 键名和字符串值共用一张表，每个包独立，不跨包保留状态
 */
internal class StringTableWriter {
    private val indices = HashMap<String, Int>()

    fun write(writer: PacketWriter, value: String) {
        val index = indices[value]
        if (index != null) {
            writer.writeVarInt(index + 1)
            return
        }
        indices[value] = indices.size
        val bytes = value.toByteArray(Charsets.UTF_8)
        writer.writeVarInt(0)
        writer.writeVarInt(bytes.size)
        writer.writeBytes(bytes)
    }
}

internal class StringTableReader {
    private val strings = ArrayList<String>()

    fun read(reader: PacketReader): String {
        val ref = reader.readVarInt()
        if (ref == 0) {
            val value = String(reader.readBytes(reader.readCount()), Charsets.UTF_8)
            strings.add(value)
            return value
        }
        val index = ref - 1
        if (index !in strings.indices) throw MalformedPacketException("string ref $ref out of range")
        return strings[index]
    }
}

internal class EncodeContext(
    val writer: PacketWriter,
    /** 字段落进附加字段区时回调（路径形如 models[].animations.foo），服务端据此告警 */
    val onSchemaMiss: ((String) -> Unit)?
) {
    val strings = StringTableWriter()
}

internal class DecodeContext(val reader: PacketReader) {
    val strings = StringTableReader()
}
