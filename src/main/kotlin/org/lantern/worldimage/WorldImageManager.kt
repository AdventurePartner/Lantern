package org.lantern.worldimage

import com.google.gson.JsonObject
import org.lantern.Lantern
import org.lantern.core.image.ImageAnims
import org.lantern.core.image.ImageAnimSpec
import org.lantern.core.image.WorldImageCodecs

/**
 * 世界图片的模板表、动画表与在播实例表。四个客户端平台共享（编进各自 source set），
 * 渲染入口 [WorldImageRenderer] 由各平台的世界渲染钩子驱动。
 *
 * 调用侧（NetworkParser 的 22/23 分支）已把收包统一调度到主线程，这里的表都是主线程访问。
 */
object WorldImageManager {

    private val templates = LinkedHashMap<String, WorldImageSpec>()
    private val animations = LinkedHashMap<String, ImageAnimSpec>()
    private val instances = LinkedHashMap<String, WorldImageInstance>()

    init {
        animations.putAll(ImageAnims.BUILT_INS)
    }

    /** opcode 22：模板与动画表整体重建（实例是瞬时态，不受配置重载影响）。 */
    fun handleConfig(obj: JsonObject) {
        // 先全量解析、后原子提交：中途任何一条抛异常都不会留下"新模板 + 旧动画"的半更新状态
        val newTemplates = LinkedHashMap<String, WorldImageSpec>()
        jsonSection(obj, "images")?.entrySet()?.forEach { (name, element) ->
            if (!element.isJsonObject) return@forEach
            val spec = runCatching { WorldImageCodec.parseSpec(element.asJsonObject, null) }
                .getOrElse {
                    Lantern.logger.warn("[Lantern] world image template '$name' 解析失败：${it.message}")
                    null
                } ?: return@forEach
            newTemplates[name] = spec
        }
        val customs = WorldImageCodecs.parseAnimations(jsonSection(obj, "animations"))

        templates.clear()
        templates.putAll(newTemplates)
        animations.clear()
        animations.putAll(ImageAnims.BUILT_INS)
        animations.putAll(customs)
        Lantern.logger.info(
            "[Lantern] world images synced: {} templates, {} animations ({} custom)",
            templates.size, animations.size, customs.size
        )
    }

    /** opcode 23：spawn 同 id = 整体替换（重启动画），remove 按 id，clear 清空。 */
    fun handleCommand(obj: JsonObject) {
        when (obj.get("action")?.takeIf { it.isJsonPrimitive }?.asString) {
            "spawn" -> {
                val array = obj.get("images")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
                array.forEach { element ->
                    if (!element.isJsonObject) return@forEach
                    val instance = runCatching {
                        WorldImageCodec.parseInstance(element.asJsonObject, templates::get, animations::get)
                    }.getOrElse {
                        Lantern.logger.warn("[Lantern] world image instance 解析失败：${it.message}")
                        null
                    } ?: return@forEach
                    instances[instance.id] = instance
                }
            }
            "remove" -> obj.get("ids")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                if (element.isJsonPrimitive) instances.remove(element.asString)
            }
            "clear" -> instances.clear()
        }
    }

    /** 断线/换服清空：实例与模板随会话作废（否则跨服残留成幽灵图），动画表回内置基线。 */
    fun clear() {
        templates.clear()
        animations.clear()
        animations.putAll(ImageAnims.BUILT_INS)
        instances.clear()
    }

    /** 渲染帧调用：先剔除到期实例，再给出快照（渲染线程与主线程同为渲染侧调用）。 */
    fun activeInstances(nowMillis: Long): List<WorldImageInstance> {
        instances.values.removeIf { instance ->
            val ticks = (nowMillis - instance.spawnAtMillis) / 50.0
            ImageAnims.finished(instance.anim, ticks) ||
                (instance.ageTicks != null && ticks >= instance.ageTicks)
        }
        return instances.values.toList()
    }

    /** 字段存在且是 JsonObject 才取出，否则 null——getAsJsonObject 对异类型字段会抛异常。 */
    private fun jsonSection(obj: JsonObject, key: String): JsonObject? =
        obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
}
