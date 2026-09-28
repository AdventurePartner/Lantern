package org.lantern.core.image

import com.google.gson.JsonObject
import org.lantern.core.CoreLog

/**
 * 协议 opcode 22 animations 段的 JSON -> [ImageAnimSpec]。
 * 只做客户端侧解析；bukkit 端不依赖 common-core，从 yml 组同构 JSON 下发。
 *
 * 动画形态：
 * `{ "duration": 40, "loop": false, "tracks": { "offset-y": [{"t":0,"v":0,"ease":"out"}, ...] } }`
 *
 * 校验失败的条目整条丢弃并记日志（CoreLog），不静默降级。
 */
object WorldImageCodecs {

    fun parseAnimations(obj: JsonObject?): Map<String, ImageAnimSpec> {
        if (obj == null) return emptyMap()
        val result = LinkedHashMap<String, ImageAnimSpec>()
        for ((name, element) in obj.entrySet()) {
            if (!element.isJsonObject) {
                CoreLog.logger.warning("world image animation '$name': 不是对象，丢弃")
                continue
            }
            val spec = parseAnimation(name, element.asJsonObject) ?: continue
            result[name] = spec
        }
        return result
    }

    fun parseAnimation(name: String, obj: JsonObject): ImageAnimSpec? {
        val duration = obj.get("duration")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (duration < 1) {
            CoreLog.logger.warning("world image animation '$name': duration 缺失或小于 1，丢弃")
            return null
        }
        val loop = obj.get("loop")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

        val tracksObj = obj.getAsJsonObject("tracks")
        val tracks = LinkedHashMap<String, List<ImageKeyframe>>()
        if (tracksObj != null) {
            for ((trackName, trackElement) in tracksObj.entrySet()) {
                if (trackName !in ImageAnims.TRACK_NAMES) {
                    CoreLog.logger.warning("world image animation '$name': 未知轨道 '$trackName'，丢弃该动画")
                    return null
                }
                if (!trackElement.isJsonArray) continue
                val frames = mutableListOf<ImageKeyframe>()
                for (frameElement in trackElement.asJsonArray) {
                    if (!frameElement.isJsonObject) continue
                    val frame = frameElement.asJsonObject
                    val hasTime = frame.get("t")?.isJsonPrimitive == true
                    val hasValue = frame.get("v")?.isJsonPrimitive == true
                    if (!hasTime || !hasValue) {
                        CoreLog.logger.warning("world image animation '$name': 轨道 '$trackName' 有帧缺 t/v，丢弃该动画")
                        return null
                    }
                    val ease = ImageEase.fromName(frame.get("ease")?.takeIf { it.isJsonPrimitive }?.asString)
                        ?: ImageEase.LINEAR
                    frames.add(ImageKeyframe(frame.get("t").asInt, frame.get("v").asDouble, ease))
                }
                if (frames.isEmpty()) continue
                frames.sortBy { it.time }
                tracks[trackName] = frames
            }
        }
        if (tracks.isEmpty()) {
            CoreLog.logger.warning("world image animation '$name': 没有可用轨道，丢弃")
            return null
        }
        return ImageAnimSpec(duration, loop, tracks)
    }
}
