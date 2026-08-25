package org.lantern.animation

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern

/**
 * 动画剪辑仓库：按动画文件 ResourceLocation（完整路径，如
 * lantern:animations/entity/tree.animation.json）从客户端资源管理器加载并缓存。
 *
 * 资源经由 Lantern 的动态资源桥（LanternPackLocal / 加密包）进入资源管理器，
 * GeckoLib 与本仓库读到的是同一份数据。
 */
object AnimationRepository {

    private val cache = ConcurrentHashMap<ResourceLocation, Map<String, ClipData>>()
    private val warned = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun clips(fileId: ResourceLocation): Map<String, ClipData>? {
        cache[fileId]?.let { return it }
        return runCatching { load(fileId) }
            .onFailure {
                if (warned.add(fileId)) {
                    Lantern.logger.warn("[Lantern] Failed to load animation file {}: {}", fileId, it.message)
                }
            }
            .getOrNull()
    }

    private fun load(fileId: ResourceLocation): Map<String, ClipData> {
        val resource = Minecraft.getInstance().resourceManager.getResource(fileId).orElse(null)
            ?: run {
                if (warned.add(fileId)) {
                    Lantern.logger.warn("[Lantern] Animation file not found: {}", fileId)
                }
                return emptyMap<String, ClipData>()
            }
        val parsed = resource.open().use { stream ->
            val bytes = stream.readBytes()
            val json = com.google.gson.JsonParser.parseString(String(bytes, Charsets.UTF_8))
            if (json !is JsonObject) emptyMap<String, ClipData>() else BedrockAnimationParser.parse(json)
        }
        cache[fileId] = parsed
        Lantern.logger.info("[Lantern] Parsed {} animation(s) from {}", parsed.size, fileId)
        return parsed
    }

    fun reset() {
        cache.clear()
        warned.clear()
    }
}
