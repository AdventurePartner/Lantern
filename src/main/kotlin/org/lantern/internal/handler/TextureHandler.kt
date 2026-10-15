package org.lantern.internal.handler

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
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

    fun isHttpUrl(path: String): Boolean = path.startsWith("http://") || path.startsWith("https://")

    fun getTexture(path: String): ResourceLocation {
        return textures[path] ?: let {
            if (isHttpUrl(path)) {
                textures[path] = def
                downloadResource(path)
                return@let def
            }

            val res = IdentifierBridge.of(Lantern.MOD_ID, path)
            if (Minecraft.getInstance().resourceManager.getResource(res).isPresent) {
                textures[path] = res
                return@let res
            }

            return@let def
        }
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

                // TextureManager 非线程安全，DynamicTexture 在渲染线程外构造会排队上传：
                // 构造与注册必须都在主线程，否则同名覆盖会在上传前关掉纹理（pixels 为 null 崩溃）
                if (isGif(bytes)) {
                    val (frames, delays) = decodeGif(bytes) ?: return@runAsync
                    Minecraft.getInstance().execute {
                        val texture = AnimatedGifTexture(frames, delays)
                        val loc = nextLocation("gif")
                        Minecraft.getInstance().textureManager.register(loc, texture)
                        animatedTextures.add(texture)
                        ownedLocations.add(loc)
                        textures[path] = loc
                    }
                } else {
                    val image = NativeImage.read(ByteArrayInputStream(bytes))
                    Minecraft.getInstance().execute {
                        val texture = DynamicTexture(image)
                        val loc = nextLocation("image")
                        Minecraft.getInstance().textureManager.register(loc, texture)
                        ownedLocations.add(loc)
                        textures[path] = loc
                    }
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
    }

    private fun nextLocation(type: String): ResourceLocation =
        IdentifierBridge.of(Lantern.MOD_ID, "dynamic/${type}_${idCounter.getAndIncrement()}")

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
