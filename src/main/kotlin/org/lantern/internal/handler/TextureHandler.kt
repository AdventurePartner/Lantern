package org.lantern.internal.handler

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
import org.lantern.internal.asset.AssetSync
import org.lantern.internal.asset.LanternAssets
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

object TextureHandler {
    private val def = MissingTextureAtlasSprite.getLocation()
    private val textures = ConcurrentHashMap<String, ResourceLocation>()
    private val animatedTextures = ConcurrentHashMap.newKeySet<AnimatedGifTexture>()
    private val ownedLocations = ConcurrentHashMap.newKeySet<ResourceLocation>()
    private val idCounter = AtomicInteger(0)

    /** 本地 gif 的解码状态：[Pending] / [Failed] / [Registered] */
    private val localGifs = ConcurrentHashMap<ResourceLocation, Any>()

    /** 每次排队解码用新实例占位，作废后迟到的结果按身份比对丢弃 */
    private class Pending

    private object Failed

    private class Registered(val location: ResourceLocation, val texture: AbstractTexture)

    private sealed interface Decoded {
        class Gif(val frames: Array<NativeImage>, val delays: IntArray) : Decoded
        class Still(val image: NativeImage) : Decoded
    }

    fun isHttpUrl(path: String): Boolean = path.startsWith("http://") || path.startsWith("https://")

    /** 原版 TextureManager 只认 PNG，gif 必须在交给它之前按扩展名截下来 */
    fun isGifPath(path: String): Boolean = path.endsWith(".gif", ignoreCase = true)

    fun getTexture(path: String): ResourceLocation {
        return textures[path] ?: let {
            if (isHttpUrl(path)) {
                textures[path] = def
                downloadResource(path)
                return@let def
            }

            val res = IdentifierBridge.of(Lantern.MOD_ID, path)
            if (isGifPath(path)) {
                return@let getLocalGif(res) ?: def
            }
            if (Minecraft.getInstance().resourceManager.getResource(res).isPresent) {
                textures[path] = res
                return@let res
            }

            return@let def
        }
    }

    /**
     * 本地 gif：后台逐帧解码后注册成动态贴图。解码完成前（或资源还在后台加载时）返回 null，
     * 调用方这一帧不画；加载结束后仍找不到、或解码失败时返回缺失贴图
     */
    fun getLocalGif(location: ResourceLocation): ResourceLocation? {
        when (val entry = localGifs[location]) {
            is Registered -> return entry.location
            is Pending -> return null
            Failed -> return def
        }
        if (!LanternAssets.hasResource(location)) {
            return if (AssetSync.isLoading) null else def
        }
        val token = Pending()
        if (localGifs.putIfAbsent(location, token) != null) return null
        CompletableFuture.supplyAsync({ readLocal(location) }, AssetSync.executor).whenComplete { decoded, error ->
            Minecraft.getInstance().execute {
                if (localGifs[location] !== token) {
                    decoded?.close()
                    return@execute
                }
                localGifs[location] = if (decoded != null) {
                    register(decoded)
                } else {
                    Lantern.logger.error("[Lantern] Failed to load local texture: {}", location, error)
                    Failed
                }
            }
        }
        return null
    }

    /** 资源变了（后台刷新带来新资源、F3+T）：释放本地 gif，下次用到时按新资源重新解码。主线程调用 */
    fun releaseLocalGifs() {
        val textureManager = Minecraft.getInstance().textureManager
        localGifs.values.forEach { entry ->
            if (entry is Registered) {
                (entry.texture as? AnimatedGifTexture)?.let(animatedTextures::remove)
                ownedLocations.remove(entry.location)
                textureManager.release(entry.location)
            }
        }
        localGifs.clear()
    }

    fun downloadResource(path: String) {
        if (!isHttpUrl(path)) return
        CompletableFuture.runAsync {
            try {
                val url = URL(path)
                val conn = url.openConnection()
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                val bytes = conn.getInputStream().use { it.readAllBytes() }
                val decoded = decode(bytes) ?: return@runAsync
                Minecraft.getInstance().execute {
                    textures[path] = register(decoded).location
                }
            } catch (e: Exception) {
                Lantern.logger.error("[Lantern] Failed to download texture: {}", path, e)
            }
        }
    }

    fun tick() {
        animatedTextures.forEach { it.tick() }
    }

    fun reload() {
        val textureManager = Minecraft.getInstance().textureManager
        ownedLocations.forEach(textureManager::release)
        ownedLocations.clear()
        textures.clear()
        animatedTextures.clear()
        localGifs.clear()
    }

    private fun nextLocation(type: String): ResourceLocation =
        IdentifierBridge.of(Lantern.MOD_ID, "dynamic/${type}_${idCounter.getAndIncrement()}")

    private fun readLocal(location: ResourceLocation): Decoded? {
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null) ?: return null
        return decode(resource.open().use { it.readAllBytes() })
    }

    private fun decode(bytes: ByteArray): Decoded? =
        if (isGif(bytes)) {
            decodeGif(bytes)?.let { (frames, delays) -> Decoded.Gif(frames, delays) }
        } else {
            Decoded.Still(NativeImage.read(ByteArrayInputStream(bytes)))
        }

    /**
     * TextureManager 非线程安全，DynamicTexture 在渲染线程外构造会排队上传：
     * 构造与注册必须都在主线程，否则同名覆盖会在上传前关掉纹理（pixels 为 null 崩溃）
     */
    private fun register(decoded: Decoded): Registered {
        val texture: AbstractTexture
        val location: ResourceLocation
        when (decoded) {
            is Decoded.Gif -> {
                texture = AnimatedGifTexture(decoded.frames, decoded.delays)
                location = nextLocation("gif")
                animatedTextures.add(texture)
            }
            is Decoded.Still -> {
                texture = DynamicTexture(decoded.image)
                location = nextLocation("image")
            }
        }
        Minecraft.getInstance().textureManager.register(location, texture)
        // 第一帧在下一次 tick 之前就要可用，否则注册后到首个 tick 之间画的是未分配的纹理
        (texture as? AnimatedGifTexture)?.tick()
        ownedLocations.add(location)
        return Registered(location, texture)
    }

    private fun Decoded.close() {
        when (this) {
            is Decoded.Gif -> frames.forEach(NativeImage::close)
            is Decoded.Still -> image.close()
        }
    }

    private fun isGif(bytes: ByteArray): Boolean {
        return bytes.size >= 6
                && bytes[0] == 0x47.toByte() // G
                && bytes[1] == 0x49.toByte() // I
                && bytes[2] == 0x46.toByte() // F
    }

    private fun decodeGif(bytes: ByteArray): Pair<Array<NativeImage>, IntArray>? {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val readers = ImageIO.getImageReadersByFormatName("gif")
        if (!readers.hasNext()) return null

        val reader = readers.next()
        reader.setInput(stream, false)

        val numFrames = reader.getNumImages(true)
        if (numFrames <= 0) return null

        val streamMeta = reader.streamMetadata
        val globalTree = streamMeta?.getAsTree("javax_imageio_gif_stream_1.0") as? IIOMetadataNode
        val screenDesc = globalTree
            ?.getElementsByTagName("LogicalScreenDescriptor")
            ?.item(0) as? IIOMetadataNode
        val logicalWidth = screenDesc?.getAttribute("logicalScreenWidth")?.toIntOrNull()
            ?: reader.getWidth(0)
        val logicalHeight = screenDesc?.getAttribute("logicalScreenHeight")?.toIntOrNull()
            ?: reader.getHeight(0)

        val canvas = BufferedImage(logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()

        val frames = mutableListOf<NativeImage>()
        val delays = mutableListOf<Int>()

        try {
            for (i in 0 until numFrames) {
                val meta = reader.getImageMetadata(i)
                val tree = meta.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode

                val gce = tree.getElementsByTagName("GraphicControlExtension")
                    .item(0) as? IIOMetadataNode
                val delay = (gce?.getAttribute("delayTime")?.toIntOrNull() ?: 10) * 10
                val disposal = gce?.getAttribute("disposalMethod") ?: "none"

                val imgDesc = tree.getElementsByTagName("ImageDescriptor")
                    .item(0) as? IIOMetadataNode
                val x = imgDesc?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0
                val y = imgDesc?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0

                val prevCanvas = if (disposal == "restoreToPrevious") {
                    BufferedImage(logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB).also {
                        it.createGraphics().apply { drawImage(canvas, 0, 0, null); dispose() }
                    }
                } else null

                val frame = reader.read(i)
                g.drawImage(frame, x, y, null)

                frames.add(toNativeImage(canvas, logicalWidth, logicalHeight))
                delays.add(if (delay <= 0) 100 else delay)

                when (disposal) {
                    "restoreToBackgroundColor" -> {
                        g.composite = AlphaComposite.Clear
                        g.fillRect(x, y, frame.width, frame.height)
                        g.composite = AlphaComposite.SrcOver
                    }
                    "restoreToPrevious" -> if (prevCanvas != null) {
                        g.composite = AlphaComposite.Src
                        g.drawImage(prevCanvas, 0, 0, null)
                        g.composite = AlphaComposite.SrcOver
                    }
                }
            }
        } finally {
            g.dispose()
            reader.dispose()
            stream.close()
        }

        if (frames.isEmpty()) return null
        return frames.toTypedArray() to delays.toIntArray()
    }

    private fun toNativeImage(img: BufferedImage, w: Int, h: Int): NativeImage {
        val nativeImage = NativeImage(w, h, false)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = img.getRGB(x, y)
                val a = (argb shr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                // NativeImage stores pixels as ABGR in int form (RGBA byte order in memory)
                nativeImage.setPixelRGBA(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
            }
        }
        return nativeImage
    }
}
