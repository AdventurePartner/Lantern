package org.lantern.core.protocol

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 二进制编解码的结果必须和 JSON 时代一致：服务端 Gson.toJson，客户端再解析，
 * 得到的就是解析器一直在用的那棵树。这里把两条路径的结果逐键比较
 */
class PacketCodecTest {

    @Test
    fun `server-shaped packets decode exactly like the old JSON path`() {
        PacketSamples.all().forEach { (packetId, payload) ->
            val misses = mutableListOf<String>()
            val decoded = roundTrip(packetId, payload, misses)
            assertEquals(canonical(viaJson(payload)), canonical(decoded), "packet $packetId differs from the JSON path")
            assertEquals(emptyList(), misses, "packet $packetId has fields outside PacketSchemas")
        }
    }

    @Test
    fun `generic packets keep key order`() {
        val screens = PacketSamples.uiScreens()
        assertEquals(viaJson(screens).toString(), roundTrip(5, screens).toString())
    }

    @Test
    fun `map fields keep key order`() {
        val packet = JsonObject()
        packet.addProperty("screen-id", "hud")
        val values = JsonObject()
        listOf("zeta", "alpha", "mid", "beta").forEach { values.addProperty(it, "v-$it") }
        packet.add("values", values)

        val decoded = roundTrip(13, packet)
        assertEquals(listOf("zeta", "alpha", "mid", "beta"), decoded.getAsJsonObject("values").keySet().toList())
    }

    @Test
    fun `unknown keys and mismatched types travel with their names`() {
        val packet = PacketSamples.animationControl()
        packet.addProperty("brand-new", 3)
        packet.addProperty("transition", "5")
        packet.addProperty("action", "teleport")
        val misses = mutableListOf<String>()

        val decoded = roundTrip(15, packet, misses)

        assertEquals(canonical(viaJson(packet)), canonical(decoded))
        assertEquals(setOf("brand-new", "transition", "action"), misses.toSet())
    }

    @Test
    fun `nested record misses report their path`() {
        val packet = PacketSamples.entityModels()
        packet.getAsJsonArray("models")[0].asJsonObject.getAsJsonObject("animations").addProperty("extra", true)
        val misses = mutableListOf<String>()

        val decoded = roundTrip(2, packet, misses)

        assertEquals(canonical(viaJson(packet)), canonical(decoded))
        assertEquals(listOf("models[].animations.extra"), misses)
    }

    @Test
    fun `null members are dropped while null array elements are kept`() {
        val packet = JsonObject()
        packet.add("gone", JsonNull.INSTANCE)
        val array = JsonArray()
        array.add(1)
        array.add(JsonNull.INSTANCE)
        array.add("x")
        packet.add("list", array)
        val typed = JsonObject()
        typed.addProperty("id", "lock")
        val locks = JsonArray()
        locks.add("move")
        locks.add(JsonNull.INSTANCE)
        typed.add("locks", locks)
        typed.add("durationMs", JsonNull.INSTANCE)

        assertEquals(canonical(viaJson(packet)), canonical(roundTrip(5, packet)))
        assertEquals(canonical(viaJson(typed)), canonical(roundTrip(21, typed)))
    }

    @Test
    fun `numbers keep the exact text and getter results of the JSON path`() {
        val packet = JsonObject()
        packet.addProperty("int", 42)
        packet.addProperty("negative", -7)
        packet.addProperty("long", Long.MAX_VALUE)
        packet.addProperty("short", 12.toShort())
        packet.addProperty("byte", (-3).toByte())
        packet.addProperty("float", 0.1f)
        packet.addProperty("float-int", 2.0f)
        packet.addProperty("double", 0.1)
        packet.addProperty("double-int", 1.0)
        packet.addProperty("double-half", 2.5)
        packet.addProperty("negative-zero", -0.0)
        packet.addProperty("huge", 1e300)
        packet.addProperty("big-decimal", BigDecimal("1.50"))
        packet.addProperty("big-long-double", 9.007199254740993E15)

        val viaJson = viaJson(packet)
        val decoded = roundTrip(5, packet)
        assertEquals(viaJson.toString(), decoded.toString())
        viaJson.keySet().forEach { key ->
            val expected = viaJson[key].asJsonPrimitive
            val actual = decoded[key].asJsonPrimitive
            assertEquals(expected.asDouble, actual.asDouble, "asDouble of $key")
            assertEquals(expected.asFloat, actual.asFloat, "asFloat of $key")
            assertEquals(expected.asString, actual.asString, "asString of $key")
        }
        assertEquals(0.1, decoded["float"].asDouble)
    }

    @Test
    fun `canonical uuids take sixteen bytes and other spellings stay strings`() {
        val uuid = UUID.randomUUID().toString()
        val canonicalPacket = JsonObject().also { it.addProperty("id", uuid) }
        val upperPacket = JsonObject().also { it.addProperty("id", uuid.uppercase()) }

        val canonicalSize = LanternProtocol.encodeS2C(5, canonicalPacket).size
        val upperSize = LanternProtocol.encodeS2C(5, upperPacket).size

        assertEquals(uuid, roundTrip(5, canonicalPacket)["id"].asString)
        assertEquals(uuid.uppercase(), roundTrip(5, upperPacket)["id"].asString)
        assertTrue(canonicalSize + 18 < upperSize, "uuid $canonicalSize B vs string $upperSize B")
    }

    @Test
    fun `repeated strings are written once per packet`() {
        val packet = JsonObject()
        val positions = JsonArray()
        repeat(100) {
            val position = JsonObject()
            position.addProperty("x", it)
            position.addProperty("y", 64)
            position.addProperty("z", -it)
            position.addProperty("v", 3)
            positions.add(position)
        }
        packet.add("positions", positions)
        val texture = "lantern:textures/block/very_long_texture_name"
        val generic = JsonObject()
        val array = JsonArray()
        repeat(100) { array.add(texture) }
        generic.add("textures", array)

        assertTrue(LanternProtocol.encodeBody(11, packet, null).size <= 100 * 8 + 4)
        assertTrue(LanternProtocol.encodeBody(5, generic, null).size < texture.length + 100 * 3)
    }

    @Test
    fun `nesting beyond the depth limit is rejected`() {
        val writer = PacketWriter()
        repeat(300) { level ->
            writer.writeVarInt(1)
            if (level == 0) {
                writer.writeVarInt(0)
                writer.writeVarInt(1)
                writer.writeByte('a'.code)
            } else {
                writer.writeVarInt(1)
            }
            writer.writeByte(BinaryJson.OBJECT)
        }
        writer.writeVarInt(0)
        val frame = byteArrayOf(5) + writer.toByteArray()

        assertNull(acceptedDecoder().decode(frame))
    }

    @Test
    fun `size report`() {
        PacketSamples.sized().forEach { (label, packetId, payload) ->
            val json = 5 + Gson().toJson(payload).toByteArray(Charsets.UTF_8).size
            val binary = LanternProtocol.encodeS2C(packetId, payload).size
            println("[size] $label: JSON $json B -> binary $binary B (${binary * 100 / json}%)")
            assertTrue(binary < json, "$label binary $binary B not smaller than JSON $json B")
        }
    }

    companion object {
        fun viaJson(payload: JsonObject): JsonObject = JsonParser.parseString(Gson().toJson(payload)).asJsonObject

        fun acceptedDecoder(): MainPayloadDecoder {
            val decoder = MainPayloadDecoder()
            decoder.decode(LanternProtocol.encodeProtocolInfo())
            return decoder
        }

        fun roundTrip(packetId: Int, payload: JsonObject, misses: MutableList<String>? = null): JsonObject {
            val decoder = acceptedDecoder()
            val frame = LanternProtocol.encodeS2C(packetId, payload) { misses?.add(it) }
            val packets = LanternProtocol.splitIntoChunks(frame, 1).mapNotNull { decoder.decode(it) }
            val packet = packets.single()
            assertEquals(packetId, packet.packetId)
            return packet.payload
        }

        /** 对象键排序后的渲染：字段表按位置编码，解码出来的键顺序是字段表顺序 */
        fun canonical(element: JsonElement): String = when {
            element.isJsonObject -> element.asJsonObject.entrySet().sortedBy { it.key }
                .joinToString(",", "{", "}") { (key, value) -> Gson().toJson(key) + ":" + canonical(value) }
            element.isJsonArray -> element.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
            else -> element.toString()
        }
    }
}
