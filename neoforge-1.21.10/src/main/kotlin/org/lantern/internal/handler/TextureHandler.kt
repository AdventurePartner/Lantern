package org.lantern.internal.handler

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.internal.asset.AssetSync
import org.lantern.internal.asset.LanternAssets
import org.lantern.platform.IdentifierBridge
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

object TextureHandler {
    private val missingTexture = MissingTextureAtlasSprite.getLocation()
    private val textures = ConcurrentHashMap<String, ResourceLocation>()
    private val animatedTextures = ConcurrentHashMap.newKeySet<AnimatedGifTexture>()
    private val ownedLocations = ConcurrentHashMap.newKeySet<ResourceLocation>()
    private val idCounter = AtomicInteger()

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

    fun getTexture(path: String): ResourceLocation = textures[path] ?: run {
        if (isHttpUrl(path)) {
            textures[path] = missingTexture
            downloadResource(path)
            missingTexture
        } else {
            val location = IdentifierBridge.of(Lantern.MOD_ID, path)
            if (isGifPath(path)) {
                getLocalGif(location) ?: missingTexture
            } else if (Minecraft.getInstance().resourceManager.getResource(location).isPresent) {
                textures[path] = location
                location
            } else {
                missingTexture
            }
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
            Failed -> return missingTexture
        }
        if (!LanternAssets.hasResource(location)) {
            return if (AssetSync.isLoading) null else missingTexture
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
                val connection = URI(path).toURL().openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                val bytes = connection.getInputStream().use { it.readAllBytes() }
                val decoded = decode(bytes) ?: return@runAsync
                Minecraft.getInstance().execute {
                    textures[path] = register(decoded).location
                }
            } catch (exception: Exception) {
                Lantern.logger.error("[Lantern] Failed to download texture: {}", path, exception)
            }
        }
    }

    fun tick() {
        animatedTextures.forEach(AnimatedGifTexture::tick)
    }

    fun reload() {
        val textureManager = Minecraft.getInstance().textureManager
        ownedLocations.forEach(textureManager::release)
        ownedLocations.clear()
        animatedTextures.clear()
        textures.clear()
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

    /** TextureManager 非线程安全：贴图的构造与注册都在主线程 */
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
                texture = DynamicTexture(Supplier { "Lantern dynamic texture" }, decoded.image)
                location = nextLocation("image")
            }
        }
        Minecraft.getInstance().textureManager.register(location, texture)
        // 第一帧在下一次 tick 之前就要可用，否则注册后到首个 tick 之间画的是空白底图
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

    private fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()

    private fun decodeGif(bytes: ByteArray): Pair<Array<NativeImage>, IntArray>? {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val readers = ImageIO.getImageReadersByFormatName("gif")
        if (!readers.hasNext()) return null
        val reader = readers.next()
        reader.setInput(stream, false)
        val frameCount = reader.getNumImages(true)
        if (frameCount <= 0) return null

        val streamTree = reader.streamMetadata
            ?.getAsTree("javax_imageio_gif_stream_1.0") as? IIOMetadataNode
        val screen = streamTree?.getElementsByTagName("LogicalScreenDescriptor")?.item(0) as? IIOMetadataNode
        val width = screen?.getAttribute("logicalScreenWidth")?.toIntOrNull() ?: reader.getWidth(0)
        val height = screen?.getAttribute("logicalScreenHeight")?.toIntOrNull() ?: reader.getHeight(0)
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = canvas.createGraphics()
        val frames = mutableListOf<NativeImage>()
        val delays = mutableListOf<Int>()

        try {
            repeat(frameCount) { index ->
                val tree = reader.getImageMetadata(index)
                    .getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
                val control = tree.getElementsByTagName("GraphicControlExtension").item(0) as? IIOMetadataNode
                val disposal = control?.getAttribute("disposalMethod") ?: "none"
                val delay = (control?.getAttribute("delayTime")?.toIntOrNull() ?: 10) * 10
                val descriptor = tree.getElementsByTagName("ImageDescriptor").item(0) as? IIOMetadataNode
                val x = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0
                val y = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0
                val previous = if (disposal == "restoreToPrevious") copyImage(canvas, width, height) else null
                val frame = reader.read(index)
                graphics.drawImage(frame, x, y, null)
                frames.add(toNativeImage(canvas, width, height))
                // 与共享版一致：只把 0 延迟（浏览器同样按 100ms 播）补成 100，20~90ms 的快帧保留
                delays.add(if (delay <= 0) 100 else delay)

                when (disposal) {
                    "restoreToBackgroundColor" -> {
                        graphics.composite = AlphaComposite.Clear
                        graphics.fillRect(x, y, frame.width, frame.height)
                        graphics.composite = AlphaComposite.SrcOver
                    }
                    "restoreToPrevious" -> if (previous != null) {
                        graphics.composite = AlphaComposite.Src
                        graphics.drawImage(previous, 0, 0, null)
                        graphics.composite = AlphaComposite.SrcOver
                    }
                }
            }
        } finally {
            graphics.dispose()
            reader.dispose()
            stream.close()
        }
        if (frames.isEmpty()) return null
        return frames.toTypedArray() to delays.toIntArray()
    }

    private fun copyImage(source: BufferedImage, width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { copy ->
            copy.createGraphics().use { it.drawImage(source, 0, 0, null) }
        }

    private fun toNativeImage(image: BufferedImage, width: Int, height: Int): NativeImage =
        NativeImage(width, height, false).also { nativeImage ->
            repeat(height) { y ->
                repeat(width) { x -> nativeImage.setPixel(x, y, image.getRGB(x, y)) }
            }
        }

    private inline fun <T : java.awt.Graphics2D, R> T.use(block: (T) -> R): R =
        try {
            block(this)
        } finally {
            dispose()
        }
}
