package org.lantern.worldimage

import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.core.image.ImageAnimSpec
import org.lantern.internal.handler.TextureHandler
import org.lantern.platform.IdentifierBridge
import java.util.Locale
import java.util.UUID

/** 渲染类型：TEXTURE = 贴图面片；TEXT = 原版字体文字（伤害数值路线）。 */
enum class ImageType { TEXTURE, TEXT }

/** 朝向：BILLBOARD = 每帧面向本地观察相机；FIXED = 固定 rot（实体 yaw/pitch 同语义，度）。 */
enum class ImageFacing { BILLBOARD, FIXED }

/** 绑定目标：PLAYER 按玩家名 / ENTITY 按实体 UUID / POSITION 固定世界坐标（可带维度键）。 */
enum class ImageBind { PLAYER, ENTITY, POSITION }

/**
 * 世界图片的渲染参数。opcode 22 的 images 段是模板表，opcode 23 spawn 的实例用
 * template 字段引用模板，其余字段按"实例 > 模板 > 默认"逐项覆盖合并。
 *
 * size 语义随类型变化：TEXTURE = 面片边长（格），TEXT = 字符行高（格，9px 映射）。
 */
class WorldImageSpec(
    val type: ImageType,
    val texturePath: String?,
    val texture: ResourceLocation?,
    val text: String,
    val color: Int,
    val size: Float,
    val scale: Float,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val rotYaw: Double,
    val rotPitch: Double,
    val facing: ImageFacing,
    val firstPersonVisible: Boolean,
    val seeThrough: Boolean
)

/**
 * 一个在播实例。动画存**已解析引用**而非名字：配置重载换表不影响在播实例。
 * 寿命规则：非循环动画播完即删；loop 动画与无动画实例按 ageTicks 到期删。
 */
class WorldImageInstance(
    val id: String,
    val spec: WorldImageSpec,
    val bind: ImageBind,
    val bindPlayer: String?,
    val bindEntity: UUID?,
    val bindWorld: String?,
    val bindX: Double,
    val bindY: Double,
    val bindZ: Double,
    val anim: ImageAnimSpec?,
    val ageTicks: Int?,
    val spawnAtMillis: Long
)

/** opcode 22/23 载荷的解析入口。校验失败记一条 warn 并丢弃该条目，不静默降级。 */
object WorldImageCodec {

    fun parseSpec(obj: JsonObject, base: WorldImageSpec?): WorldImageSpec? {
        val rawType = primitive(obj, "type")?.lowercase(Locale.ROOT)
        val type = when (rawType) {
            null -> base?.type ?: ImageType.TEXTURE
            "texture" -> ImageType.TEXTURE
            "text" -> ImageType.TEXT
            else -> {
                Lantern.logger.warn("[Lantern] world image: 未知 type '$rawType'，丢弃")
                return null
            }
        }

        val rawFacing = primitive(obj, "facing")?.lowercase(Locale.ROOT)
        val facing = when (rawFacing) {
            null -> base?.facing ?: ImageFacing.BILLBOARD
            "billboard" -> ImageFacing.BILLBOARD
            "fixed" -> ImageFacing.FIXED
            else -> {
                Lantern.logger.warn("[Lantern] world image: 未知 facing '$rawFacing'，丢弃")
                return null
            }
        }

        val texturePath = primitive(obj, "texture") ?: base?.texturePath
        val texture = texturePath?.let { path ->
            if (TextureHandler.isHttpUrl(path)) TextureHandler.getTexture(path)
            else IdentifierBridge.of(Lantern.MOD_ID, path)
        }
        val text = primitive(obj, "text") ?: base?.text ?: ""

        // 类型完备性：texture 型必须有贴图，text 型必须有内容
        if (type == ImageType.TEXTURE && texture == null) {
            Lantern.logger.warn("[Lantern] world image: texture 型缺少 texture 路径，丢弃")
            return null
        }
        if (type == ImageType.TEXT && text.isBlank()) {
            Lantern.logger.warn("[Lantern] world image: text 型缺少 text 内容，丢弃")
            return null
        }

        val offset = doubleTriple(obj.get("offset")) ?: base?.let { Triple(it.offsetX, it.offsetY, it.offsetZ) }
            ?: Triple(0.0, 0.0, 0.0)
        val rot = doublePair(obj.get("rot")) ?: base?.let { it.rotYaw to it.rotPitch } ?: (0.0 to 0.0)

        return WorldImageSpec(
            type = type,
            texturePath = texturePath,
            texture = texture,
            text = text,
            color = parseColor(primitive(obj, "color")) ?: base?.color ?: WHITE,
            size = (primitive(obj, "size")?.toDoubleOrNull() ?: base?.size?.toDouble()
                ?: if (type == ImageType.TEXT) 0.4 else 1.0).toFloat(),
            scale = (primitive(obj, "scale")?.toDoubleOrNull() ?: base?.scale?.toDouble() ?: 1.0).toFloat(),
            offsetX = offset.first,
            offsetY = offset.second,
            offsetZ = offset.third,
            rotYaw = rot.first,
            rotPitch = rot.second,
            facing = facing,
            firstPersonVisible = primitive(obj, "first-person")?.toBooleanStrictOrNull()
                ?: base?.firstPersonVisible ?: true,
            seeThrough = primitive(obj, "see-through")?.toBooleanStrictOrNull() ?: base?.seeThrough ?: false
        )
    }

    fun parseInstance(
        obj: JsonObject,
        templates: (String) -> WorldImageSpec?,
        animations: (String) -> ImageAnimSpec?
    ): WorldImageInstance? {
        val id = primitive(obj, "id")?.takeIf { it.isNotBlank() }
        if (id == null) {
            Lantern.logger.warn("[Lantern] world image instance: 缺少 id，丢弃")
            return null
        }

        val templateName = primitive(obj, "template")
        val base = templateName?.let { templates(it) }
        if (templateName != null && base == null) {
            Lantern.logger.warn("[Lantern] world image instance '$id': 未知模板 '$templateName'，丢弃")
            return null
        }
        val spec = parseSpec(obj, base) ?: return null

        // bind 三形态：{"player": 名字} / {"entity": uuid} / {"pos": [x,y,z], "world": 维度键(可省)}
        val bindObj = obj.getAsJsonObject("bind")
        var bind = ImageBind.POSITION
        var bindPlayer: String? = null
        var bindEntity: UUID? = null
        var bindWorld: String? = null
        var bindX = 0.0
        var bindY = 0.0
        var bindZ = 0.0
        if (bindObj != null && bindObj.get("player")?.isJsonPrimitive == true) {
            bind = ImageBind.PLAYER
            bindPlayer = bindObj.get("player").asString
        } else if (bindObj != null && bindObj.get("entity")?.isJsonPrimitive == true) {
            val uuid = runCatching { UUID.fromString(bindObj.get("entity").asString) }.getOrNull()
            if (uuid == null) {
                Lantern.logger.warn("[Lantern] world image instance '$id': entity uuid 非法，丢弃")
                return null
            }
            bind = ImageBind.ENTITY
            bindEntity = uuid
        } else if (bindObj != null && bindObj.get("pos")?.isJsonArray == true) {
            val pos = doubleTriple(bindObj.get("pos"))
            if (pos == null) {
                Lantern.logger.warn("[Lantern] world image instance '$id': pos 不是 [x,y,z]，丢弃")
                return null
            }
            bind = ImageBind.POSITION
            bindWorld = bindObj.get("world")?.takeIf { it.isJsonPrimitive }?.asString
            bindX = pos.first
            bindY = pos.second
            bindZ = pos.third
        } else {
            Lantern.logger.warn("[Lantern] world image instance '$id': 缺少 bind（player/entity/pos 三选一），丢弃")
            return null
        }

        val animName = primitive(obj, "animation")?.takeIf { it.isNotBlank() }
        val anim = if (animName != null) {
            val resolved = animations(animName)
            if (resolved == null) {
                Lantern.logger.warn("[Lantern] world image instance '$id': 未知动画 '$animName'，丢弃")
                return null
            }
            resolved
        } else {
            null
        }
        val age = primitive(obj, "age")?.toIntOrNull()?.takeIf { it > 0 }
        if (anim == null && age == null) {
            Lantern.logger.warn("[Lantern] world image instance '$id': 无动画且缺 age，没有寿命，丢弃")
            return null
        }

        return WorldImageInstance(
            id, spec, bind, bindPlayer, bindEntity, bindWorld, bindX, bindY, bindZ,
            anim, age, Util.getMillis()
        )
    }

    /** "#RRGGBB" / "#AARRGGBB" / 十进制整数。 */
    fun parseColor(raw: String?): Int? {
        if (raw == null) return null
        val s = raw.trim()
        if (s.startsWith("#")) {
            val hex = s.substring(1)
            return when (hex.length) {
                6 -> (0xFF shl 24) or hex.toInt(16)
                8 -> hex.toLong(16).toInt()
                else -> null
            }
        }
        return s.toIntOrNull()
    }

    private const val WHITE = 0xFFFFFFFF.toInt()

    private fun primitive(obj: JsonObject, key: String): String? =
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun doubleTriple(element: com.google.gson.JsonElement?): Triple<Double, Double, Double>? {
        if (element == null || !element.isJsonArray || element.asJsonArray.size() < 3) return null
        val array = element.asJsonArray
        val x = array.get(0).takeIf { it.isJsonPrimitive }?.asDouble ?: return null
        val y = array.get(1).takeIf { it.isJsonPrimitive }?.asDouble ?: return null
        val z = array.get(2).takeIf { it.isJsonPrimitive }?.asDouble ?: return null
        return Triple(x, y, z)
    }

    private fun doublePair(element: com.google.gson.JsonElement?): Pair<Double, Double>? {
        if (element == null || !element.isJsonArray || element.asJsonArray.size() < 2) return null
        val array = element.asJsonArray
        val a = array.get(0).takeIf { it.isJsonPrimitive }?.asDouble ?: return null
        val b = array.get(1).takeIf { it.isJsonPrimitive }?.asDouble ?: return null
        return a to b
    }
}
