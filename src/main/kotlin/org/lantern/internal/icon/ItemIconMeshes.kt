package org.lantern.internal.icon

import net.minecraft.client.Minecraft
import org.lantern.Lantern
import org.lantern.internal.asset.AssetSync
import org.lantern.internal.handler.ResourceHandler
import org.lantern.platform.IdentifierBridge
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * 物品图标网格缓存（按 CustomModelData）。图标不再依赖模型烘焙与方块图集：
 * 首次用到时在 [AssetSync.executor] 后台读贴图生成网格，生成完之前调用方画原版物品
 */
object ItemIconMeshes {

    /** 每次排队生成用新实例占位，作废后迟到的结果按身份比对丢弃 */
    private class Pending

    private object Failed

    /** 该 CustomModelData 没配成图标（插件常拿它做别的用途），记下免得每帧再查定义 */
    private object NotIcon

    private val entries = ConcurrentHashMap<Int, Any>()
    private val warnedTextures = ConcurrentHashMap.newKeySet<String>()

    /** 图标定义或资源变了（packet 4、后台刷新、F3+T）：丢掉全部网格，下次用到时重新生成 */
    @JvmStatic
    fun invalidate() {
        entries.clear()
    }

    /** 网格已生成时返回；否则安排后台生成并返回 null，调用方这一帧画原版物品 */
    @JvmStatic
    fun get(customModelData: Int): IconMesh? {
        when (val entry = entries[customModelData]) {
            is IconMesh -> return entry
            null -> Unit
            else -> return null
        }
        val spec = ResourceHandler.getItemIconSpec(customModelData) ?: run {
            entries.putIfAbsent(customModelData, NotIcon)
            return null
        }
        val token = Pending()
        if (entries.putIfAbsent(customModelData, token) != null) return null
        CompletableFuture.supplyAsync({ load(spec) }, AssetSync.executor).whenComplete { mesh, error ->
            if (error != null) {
                Lantern.logger.warn("[Lantern] Failed to build item icon {}: {}", spec.identifier, error.message)
            }
            entries.replace(customModelData, token, mesh ?: Failed)
        }
        return null
    }

    private fun load(spec: IconSpec): IconMesh? {
        val parsed = IdentifierBridge.parse(spec.texture)
        val texture = IdentifierBridge.of(parsed.namespace, "textures/${parsed.path}.png")
        val resource = Minecraft.getInstance().resourceManager.getResource(texture).orElse(null)
        if (resource == null) {
            // 资源还在后台加载时缺贴图是正常的，加载完成的刷新会作废这条结果重新生成
            if (!AssetSync.isLoading && warnedTextures.add(spec.texture)) {
                Lantern.logger.warn("[Lantern] Item icon texture not found: {} (icon {})", texture, spec.identifier)
            }
            return null
        }
        val image = resource.open().use(ImageIO::read) ?: return null
        val width = image.width
        val height = image.height
        // 动画贴图多帧竖排，只取第一帧
        val frameHeight = if (height > width && height % width == 0) width else height
        val argb = image.getRGB(0, 0, width, frameHeight, null, 0, width)
        return IconMeshBuilder.build(texture, spec.type == "handheld", argb, width, frameHeight, height)
    }
}
