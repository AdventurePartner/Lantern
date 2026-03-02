package org.lantern.internal.handler

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object TextureHandler {
    private val def = MissingTextureAtlasSprite.getLocation()
    private val textures = ConcurrentHashMap<String, ResourceLocation>()

    fun getTexture(path: String): ResourceLocation {
        return textures[path] ?: let {
            if (path.startsWith("https://")) {
                textures[path] = def
                downloadResource(path)
                return@let def
            }

            val res = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, path)
            if (Minecraft.getInstance().resourceManager.getResource(res).isPresent) {
                textures[path] = res
                return@let res
            }

            return@let def
        }
    }

    fun downloadResource(path: String) {
        if (!path.startsWith("https://")) {
            return
        }
        CompletableFuture.runAsync {
            try {
                val url = URL(path)
                val conn = url.openConnection()
                conn.getInputStream().use {
                    val image = NativeImage.read(it)
                    val texture = DynamicTexture(image)
                    val dynamicLoc = Minecraft.getInstance().getTextureManager()
                        .register("lantern_dynamic_res", texture)
                    Minecraft.getInstance().execute { textures[path] = dynamicLoc }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun reload() {
        textures.clear()
    }
}