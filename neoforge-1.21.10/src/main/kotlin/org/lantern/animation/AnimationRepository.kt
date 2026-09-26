package org.lantern.animation

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.core.anim.clip.BedrockAnimationParser
import org.lantern.core.anim.clip.ClipData

/**
 * 动画剪辑仓库：按动画库 id（字符串，如 lantern:animations/entity/tree.animation.json，
 * 裸路径补 mod 命名空间）从客户端资源管理器加载并缓存。
 *
 * 资源经由 Lantern 的动态资源桥（LanternPackLocal / 加密包）进入资源管理器，
 * GeckoLib 与本仓库读到的是同一份数据。
 */
object AnimationRepository {

    private val cache = ConcurrentHashMap<String, Map<String, ClipData>>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun clips(fileId: String): Map<String, ClipData>? {
        cache[fileId]?.let { return it }
        return runCatching { load(fileId) }
            .onFailure {
                if (warned.add(fileId)) {
                    Lantern.logger.warn("[Lantern] Failed to load animation file {}: {}", fileId, it.message)
                }
            }
            .getOrNull()
    }

    private fun load(fileId: String): Map<String, ClipData> {
        val withNs = if (fileId.contains(':')) fileId else "${Lantern.MOD_ID}:$fileId"
        val location = runCatching { ResourceLocation.parse(withNs) }.getOrNull() ?: run {
            if (warned.add(fileId)) {
                Lantern.logger.warn("[Lantern] Invalid animation file id: {}", fileId)
            }
            return emptyMap<String, ClipData>()
        }
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
            ?: run {
                if (warned.add(fileId)) {
                    Lantern.logger.warn("[Lantern] Animation file not found: {}", fileId)
                }
                return emptyMap<String, ClipData>()
            }
        val parsed = resource.open().use { stream ->
            val bytes = stream.readBytes()
            val json = com.google.gson.JsonParser.parseString(String(bytes, Charsets.UTF_8))
            if (json !is JsonObject) emptyMap<String, ClipData>() else BedrockAnimationParser.parse(json, GeckoLibMolang)
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
