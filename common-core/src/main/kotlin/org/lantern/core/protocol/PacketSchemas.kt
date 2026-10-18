package org.lantern.core.protocol

import java.util.zip.CRC32

/**
 * lantern:main 各 S2C 包的字段表，服务端编码与四端客户端解码共用这一份。
 *
 * 字段按位置编码，两端的表必须完全一致：这里任何改动（增删字段、换顺序、改类型、
 * 改 choice 取值）都要把 [LanternProtocol.PROTOCOL_VERSION] 加一，PacketSchemasTest
 * 的指纹断言会拦住漏改。服务端临时多写的键不需要改表，会带键名走附加字段区。
 * 没有登记的包（5 UI 界面、22 世界图片模板）整包走通用编码。
 */
internal object PacketSchemas {

    private val XYZ = record(num("x"), num("y"), num("z"))

    private val schemas: Map<Int, RecordSchema> = mapOf(
        1 to record(
            list(
                "characters",
                record(
                    str("character"), str("texture"), num("width"), num("height"), num("wide"),
                    num("offset-x"), num("offset-y")
                )
            )
        ),
        2 to record(
            list(
                "models",
                record(
                    str("name"), str("geo"), str("texture"),
                    obj("animations", record(str("file"), tree("states"))),
                    str("animation"), num("scale"), num("height"), num("width"), bool("hidden"), num("offset-y")
                )
            )
        ),
        3 to record(list("keys", record(str("key"), bool("press")))),
        4 to record(list("icons", record(int("data"), str("identifier"), str("texture"), str("type")))),
        6 to record(str("screen-id")),
        7 to record(str("key")),
        8 to record(
            list(
                "costumes",
                record(
                    str("id"), str("display-name"), str("geo"), str("texture"),
                    obj(
                        "animations",
                        record(str("file"), tree("states"), list("upper-body-bones", FieldType.Str))
                    ),
                    bool("host-driven"), num("scale"), obj("offset", XYZ), str("slot"), bool("bone-sync"),
                    obj(
                        "bone-mapping",
                        record(
                            str("head"), str("body"), str("left_arm"), str("right_arm"),
                            str("left_leg"), str("right_leg")
                        )
                    )
                )
            )
        ),
        9 to record(
            list("assignments", record(uuid("uuid"), list("costumes", record(str("slot"), str("costume"))))),
            list("removals", record(uuid("uuid"), str("slot")))
        ),
        10 to record(
            list(
                "blocks",
                record(
                    str("id"), int("custom_variation"), int("custom_model_data"),
                    str("geo"), str("texture"), str("animation"), num("scale"), str("idle_animation"),
                    num("block_scale"), num("item_offset_x"), num("item_offset_y"), num("item_offset_z"),
                    num("hardness"), str("preferred_tool"), str("break_sound")
                )
            )
        ),
        11 to record(list("positions", record(int("x"), int("y"), int("z"), int("v")))),
        12 to record(choice("action", "add", "remove"), int("x"), int("y"), int("z"), int("v")),
        13 to record(str("screen-id"), map("values", FieldType.Str)),
        14 to record(
            list(
                "channels",
                record(
                    str("id"), str("display-name"), list("prefixes", FieldType.Str), list("filter", FieldType.Str),
                    tree("style")
                )
            ),
            tree("style")
        ),
        15 to record(
            uuid("uuid"),
            choice("action", "play", "stop", "pause", "resume", "seek"),
            str("animation"),
            int("transition"),
            choice("mode", "loop", "once"),
            num("speed"),
            num("time"),
            bool("uninterruptible"),
            str("layer"),
            str("file"),
            int("seq"),
            int("exit")
        ),
        17 to record(uuid("uuid"), map("vars", FieldType.Str)),
        // 18 按 action 分好几种形态，这里取所有形态字段的并集，没出现的字段只占一位有无标记
        18 to record(
            choice("action", "shoulder", "lock", "unlock", "shake", "fov", "offset", "clear", "path", "watch"),
            bool("enabled"), num("offset-x"), num("offset-y"), num("distance"),
            obj("target", XYZ), uuid("entity"), num("smooth"), num("duration"), bool("sync"),
            num("amplitude"), num("frequency"), bool("decay"),
            num("value"), num("transition"),
            num("pitch"), num("yaw"), num("roll"),
            num("speed"), bool("loop"),
            list(
                "keyframes",
                record(
                    int("t"), num("x"), num("y"), num("z"), num("yaw"), num("pitch"), num("fov"),
                    choice("interp", "linear", "smooth", "hold")
                )
            ),
            obj("pos", XYZ), obj("look", XYZ)
        ),
        19 to record(
            list(
                "actions",
                record(
                    str("id"), str("key"), str("file"), int("transition"), int("exit-transition"), num("speed"),
                    int("cooldown"), str("layer"), bool("uninterruptible"), bool("exclusive"),
                    int("suppress-vanilla-attack"), int("invulnerable"), bool("airborne"), bool("require-ground"),
                    num("distance"), int("dash-duration"), num("vertical"), str("trigger"), bool("server-checked"),
                    str("costume"), map("directions", FieldType.Str),
                    list(
                        "steps",
                        record(
                            str("animation"), num("cancel-at"), int("window"), int("transition"),
                            int("exit-transition")
                        )
                    )
                )
            )
        ),
        20 to record(
            uuid("follower"), uuid("host"), list("offset", FieldType.Num), bool("rotate"), bool("visible"),
            int("durationMs")
        ),
        21 to record(str("id"), list("locks", FieldType.Str), int("durationMs"), bool("clear")),
        23 to record(choice("action", "spawn", "remove", "clear"), tree("images")),
        99 to record(bool("full"))
    )

    fun of(packetId: Int): RecordSchema? = schemas[packetId]

    /** 字段表的结构摘要，PacketSchemasTest 用它确认改表时同步升了协议版本 */
    fun fingerprint(): String {
        val text = buildString {
            schemas.toSortedMap().forEach { (id, schema) ->
                append(id).append('=')
                describe(this, schema)
                append(';')
            }
        }
        val crc = CRC32()
        crc.update(text.toByteArray(Charsets.UTF_8))
        return java.lang.Long.toHexString(crc.value)
    }

    private fun describe(out: StringBuilder, schema: RecordSchema) {
        out.append('{')
        schema.fields.forEach { field ->
            out.append(field.name).append(':')
            describe(out, field.type)
            out.append(',')
        }
        out.append('}')
    }

    private fun describe(out: StringBuilder, type: FieldType) {
        when (type) {
            FieldType.Str -> out.append("s")
            FieldType.Integral -> out.append("i")
            FieldType.Num -> out.append("n")
            FieldType.Bool -> out.append("b")
            FieldType.Uuid -> out.append("u")
            FieldType.Tree -> out.append("t")
            is FieldType.Choice -> out.append("c").append(type.values)
            is FieldType.Obj -> describe(out, type.schema)
            is FieldType.ListOf -> {
                out.append('[')
                describe(out, type.element)
                out.append(']')
            }
            is FieldType.MapOf -> {
                out.append("m<")
                describe(out, type.value)
                out.append('>')
            }
        }
    }

    private fun record(vararg fields: Field) = RecordSchema(fields.toList())
    private fun str(name: String) = Field(name, FieldType.Str)
    private fun int(name: String) = Field(name, FieldType.Integral)
    private fun num(name: String) = Field(name, FieldType.Num)
    private fun bool(name: String) = Field(name, FieldType.Bool)
    private fun uuid(name: String) = Field(name, FieldType.Uuid)
    private fun tree(name: String) = Field(name, FieldType.Tree)
    private fun choice(name: String, vararg values: String) = Field(name, FieldType.Choice(values.toList()))
    private fun obj(name: String, schema: RecordSchema) = Field(name, FieldType.Obj(schema))
    private fun list(name: String, element: FieldType) = Field(name, FieldType.ListOf(element))
    private fun list(name: String, element: RecordSchema) = Field(name, FieldType.ListOf(FieldType.Obj(element)))
    private fun map(name: String, value: FieldType) = Field(name, FieldType.MapOf(value))
}
