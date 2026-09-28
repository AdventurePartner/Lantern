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
        val newTemplates = LinkedHashMap<String, WorldImageSpec>()
        obj.getAsJsonObject("images")?.entrySet()?.forEach { (name, element) ->
            if (!element.isJsonObject) return@forEach
            val spec = WorldImageCodec.parseSpec(element.asJsonObject, null) ?: return@forEach
            newTemplates[name] = spec
        }
        templates.clear()
        templates.putAll(newTemplates)

        val customs = WorldImageCodecs.parseAnimations(obj.getAsJsonObject("animations"))
        animations.clear()
        animations.putAll(ImageAnims.BUILT_INS)
        animations.putAll(customs)
        Lantern.logger.info(
            "[Lantern] world images synced: {} templates, {} animations ({} custom)",
            templates.size, animations.size, customs.size
        )
    }

    /** opcode 23：spawn 同 id = 整体替换（重启动画，即外部"更新"语义），remove 按 id，clear 清空。 */
    fun handleCommand(obj: JsonObject) {
        when (obj.get("action")?.takeIf { it.isJsonPrimitive }?.asString) {
            "spawn" -> {
                val array = obj.getAsJsonArray("images") ?: return
                array.forEach { element ->
                    if (!element.isJsonObject) return@forEach
                    val instance = WorldImageCodec.parseInstance(
                        element.asJsonObject, templates::get, animations::get
                    ) ?: return@forEach
                    instances[instance.id] = instance
                }
            }
            "remove" -> obj.getAsJsonArray("ids")?.forEach { element ->
                if (element.isJsonPrimitive) instances.remove(element.asString)
            }
            "clear" -> instances.clear()
        }
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
}
