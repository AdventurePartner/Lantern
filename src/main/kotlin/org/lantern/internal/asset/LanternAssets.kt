package org.lantern.internal.asset

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import org.lantern.platform.GeckoCacheBridge
import org.lantern.platform.IdentifierBridge
import java.util.concurrent.ConcurrentHashMap

/**
 * 渲染入口的「资源是否就绪」判定。资源存在性按批缓存（每帧逐实体查资源管理器会落到磁盘），
 * 资源集合只会在加密包发布、后台刷新、F3+T、断线时变化，届时 [invalidate]
 */
object LanternAssets {

    private val present = ConcurrentHashMap<ResourceLocation, Boolean>()

    @JvmStatic
    fun invalidate() {
        present.clear()
    }

    @JvmStatic
    fun hasResource(location: ResourceLocation): Boolean =
        present.computeIfAbsent(location) { Minecraft.getInstance().resourceManager.getResource(it).isPresent }

    @JvmStatic
    fun isGeoReady(location: ResourceLocation): Boolean = GeckoCacheBridge.hasModel(location)

    /**
     * url 非空时是远程贴图，下载完成前 TextureHandler 返回缺失贴图。
     * 本地贴图只在资源加载期间按存在性挡住；加载结束后缺的贴图照常交给渲染，
     * 紫黑格子用来暴露配置错误，运行时注册的动态贴图也不受影响
     */
    @JvmStatic
    fun isTextureReady(location: ResourceLocation, url: String?): Boolean {
        val missing = MissingTextureAtlasSprite.getLocation()
        if (url != null) return TextureHandler.getTexture(url) != missing
        if (location == missing) return false
        return !AssetSync.isLoading || hasResource(location)
    }

    /**
     * TextureHandler 写法的贴图路径（http URL 或 lantern 命名空间下的相对路径），规则同 [isTextureReady]；
     * 本地 gif 要等后台解码完
     */
    @JvmStatic
    fun isTexturePathReady(path: String): Boolean {
        if (TextureHandler.isHttpUrl(path)) return isTextureReady(MissingTextureAtlasSprite.getLocation(), path)
        if (TextureHandler.isGifPath(path)) {
            return TextureHandler.getLocalGif(IdentifierBridge.of(Lantern.MOD_ID, path)) != null
        }
        return !AssetSync.isLoading || hasResource(IdentifierBridge.of(Lantern.MOD_ID, path))
    }

    @JvmStatic
    fun isModelReady(model: ResourceLocation, texture: ResourceLocation, textureUrl: String?): Boolean =
        isGeoReady(model) && isTextureReady(texture, textureUrl)
}
