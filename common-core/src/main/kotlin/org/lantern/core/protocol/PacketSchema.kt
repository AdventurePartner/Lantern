package org.lantern.core.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** 字段表里一个字段的线上类型 */
internal sealed class FieldType {
    /** 字符串，走包内字符串表 */
    object Str : FieldType()

    /** Byte / Short / Integer / Long，zigzag varlong，无类型标签 */
    object Integral : FieldType()

    /** 任意 Number，带 1 字节数字标签以保留 Java 类型 */
    object Num : FieldType()

    object Bool : FieldType()

    /** 规范小写 UUID 字符串，16 字节 */
    object Uuid : FieldType()

    /** 开放结构，走 [BinaryJson] 通用编码 */
    object Tree : FieldType()

    /** 取值固定的字符串，1 字节下标 */
    class Choice(val values: List<String>) : FieldType() {
        init {
            require(values.size in 1..256) { "choice needs 1..256 values" }
            require(values.toSet().size == values.size) { "duplicate choice values: $values" }
        }
        val indices: Map<String, Int> = values.withIndex().associate { (index, value) -> value to index }
    }

    class Obj(val schema: RecordSchema) : FieldType()

    class ListOf(val element: FieldType) : FieldType()

    /** 动态键的对象（键名本身是数据），值统一为 [value] 类型，保留键顺序 */
    class MapOf(val value: FieldType) : FieldType()
}

internal class Field(val name: String, val type: FieldType)

/**
 * 记录型对象的字段表：线上按字段顺序写值，不写键名。
 *
 * 一个对象编码为：
 *   varlong 有无标记（bit0 = 有附加字段，bit(i+1) = 第 i 个字段存在）
 *   各存在字段的值（按字段表顺序）
 *   [附加字段区] varint 个数 + (键, 通用值)…
 *
 * 字段表里没有的键、或者值的类型与字段表不符，都带键名写进附加字段区：
 * 服务端代码和字段表对不上时只是多花字节，不会丢数据
 */
internal class RecordSchema(val fields: List<Field>) {
    init {
        require(fields.size <= MAX_FIELDS) { "a record holds at most $MAX_FIELDS fields" }
        require(fields.map { it.name }.toSet().size == fields.size) { "duplicate field names" }
    }

    private val indexByName: Map<String, Int> = fields.withIndex().associate { (index, field) -> field.name to index }

    fun indexOf(name: String): Int = indexByName[name] ?: -1

    companion object {
        const val MAX_FIELDS = 62
    }
}

internal object RecordCodec {

    fun write(ctx: EncodeContext, schema: RecordSchema, obj: JsonObject, path: String, depth: Int) {
        checkDepth(depth)
        val fields = schema.fields
        val placed = arrayOfNulls<JsonElement>(fields.size)
        var mask = 0L
        fields.forEachIndexed { index, field ->
            val value = obj.get(field.name) ?: return@forEachIndexed
            if (value.isJsonNull || !fits(field.type, value)) return@forEachIndexed
            placed[index] = value
            mask = mask or (1L shl (index + 1))
        }
        val extras = obj.entrySet().filter { (key, value) ->
            if (value.isJsonNull) return@filter false
            val index = schema.indexOf(key)
            index < 0 || placed[index] == null
        }
        if (extras.isNotEmpty()) mask = mask or 1L

        ctx.writer.writeVarLong(mask)
        fields.forEachIndexed { index, field ->
            val value = placed[index] ?: return@forEachIndexed
            writeTyped(ctx, field.type, value, child(path, field.name), depth + 1)
        }
        if (extras.isNotEmpty()) {
            ctx.writer.writeVarInt(extras.size)
            extras.forEach { (key, value) ->
                ctx.onSchemaMiss?.invoke(child(path, key))
                ctx.strings.write(ctx.writer, key)
                BinaryJson.writeValue(ctx, value, depth + 1)
            }
        }
    }

    fun read(ctx: DecodeContext, schema: RecordSchema, depth: Int): JsonObject {
        checkDepth(depth)
        val fields = schema.fields
        val mask = ctx.reader.readVarLong()
        if (mask ushr (fields.size + 1) != 0L) {
            throw MalformedPacketException("presence mask has bits beyond ${fields.size} fields")
        }
        val obj = JsonObject()
        fields.forEachIndexed { index, field ->
            if (mask and (1L shl (index + 1)) == 0L) return@forEachIndexed
            obj.add(field.name, readTyped(ctx, field.type, depth + 1))
        }
        if (mask and 1L != 0L) {
            repeat(ctx.reader.readCount()) {
                val key = ctx.strings.read(ctx.reader)
                obj.add(key, BinaryJson.readValue(ctx, depth + 1))
            }
        }
        return obj
    }

    /** 值能否按字段类型原样写出并原样读回。Obj 只看是不是对象：内部不符的字段由下一层落进附加字段区 */
    private fun fits(type: FieldType, value: JsonElement): Boolean = when (type) {
        FieldType.Str -> value.isJsonPrimitive && value.asJsonPrimitive.isString
        FieldType.Integral -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
            value.asJsonPrimitive.asNumber.let { it is Int || it is Long || it is Short || it is Byte }
        FieldType.Num -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber
        FieldType.Bool -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
        FieldType.Uuid -> value.isJsonPrimitive && value.asJsonPrimitive.isString &&
            BinaryJson.parseCanonicalUuid(value.asString) != null
        FieldType.Tree -> true
        is FieldType.Choice -> value.isJsonPrimitive && value.asJsonPrimitive.isString &&
            value.asString in type.indices
        is FieldType.Obj -> value.isJsonObject
        // 数组里的 null 按 JSON 语义要保留，类型化列表写不了，整个字段交给通用编码
        is FieldType.ListOf -> value.isJsonArray && value.asJsonArray.all { !it.isJsonNull && fits(type.element, it) }
        is FieldType.MapOf -> value.isJsonObject &&
            value.asJsonObject.entrySet().all { (_, v) -> v.isJsonNull || fits(type.value, v) }
    }

    private fun writeTyped(ctx: EncodeContext, type: FieldType, value: JsonElement, path: String, depth: Int) {
        val writer = ctx.writer
        when (type) {
            FieldType.Str -> ctx.strings.write(writer, value.asString)
            FieldType.Integral -> writer.writeSignedVarLong(value.asJsonPrimitive.asNumber.toLong())
            FieldType.Num -> BinaryJson.writeNumber(ctx, value.asJsonPrimitive.asNumber)
            FieldType.Bool -> writer.writeByte(if (value.asBoolean) 1 else 0)
            FieldType.Uuid -> writer.writeUuid(BinaryJson.parseCanonicalUuid(value.asString)!!)
            FieldType.Tree -> BinaryJson.writeValue(ctx, value, depth)
            is FieldType.Choice -> writer.writeByte(type.indices.getValue(value.asString))
            is FieldType.Obj -> write(ctx, type.schema, value.asJsonObject, path, depth)
            is FieldType.ListOf -> {
                val array = value.asJsonArray
                writer.writeVarInt(array.size())
                val elementPath = "$path[]"
                array.forEach { writeTyped(ctx, type.element, it, elementPath, depth + 1) }
            }
            is FieldType.MapOf -> {
                val entries = value.asJsonObject.entrySet().filter { !it.value.isJsonNull }
                writer.writeVarInt(entries.size)
                val valuePath = "$path{}"
                entries.forEach { (key, entryValue) ->
                    ctx.strings.write(writer, key)
                    writeTyped(ctx, type.value, entryValue, valuePath, depth + 1)
                }
            }
        }
    }

    private fun readTyped(ctx: DecodeContext, type: FieldType, depth: Int): JsonElement {
        val reader = ctx.reader
        return when (type) {
            FieldType.Str -> JsonPrimitive(ctx.strings.read(reader))
            FieldType.Integral -> BinaryJson.numberOf(reader.readSignedVarLong().toString())
            FieldType.Num -> BinaryJson.readNumber(ctx, reader.readByte())
            FieldType.Bool -> when (reader.readByte()) {
                0 -> JsonPrimitive(false)
                1 -> JsonPrimitive(true)
                else -> throw MalformedPacketException("invalid boolean byte")
            }
            FieldType.Uuid -> JsonPrimitive(reader.readUuid().toString())
            FieldType.Tree -> BinaryJson.readValue(ctx, depth)
            is FieldType.Choice -> {
                val index = reader.readByte()
                JsonPrimitive(type.values.getOrNull(index) ?: throw MalformedPacketException("choice index $index out of range"))
            }
            is FieldType.Obj -> read(ctx, type.schema, depth)
            is FieldType.ListOf -> {
                val array = JsonArray()
                repeat(reader.readCount()) { array.add(readTyped(ctx, type.element, depth + 1)) }
                array
            }
            is FieldType.MapOf -> {
                val obj = JsonObject()
                repeat(reader.readCount()) {
                    val key = ctx.strings.read(reader)
                    obj.add(key, readTyped(ctx, type.value, depth + 1))
                }
                obj
            }
        }
    }

    private fun child(path: String, name: String): String = if (path.isEmpty()) name else "$path.$name"

    private fun checkDepth(depth: Int) {
        if (depth > BinaryJson.MAX_DEPTH) throw MalformedPacketException("nesting deeper than ${BinaryJson.MAX_DEPTH}")
    }
}
