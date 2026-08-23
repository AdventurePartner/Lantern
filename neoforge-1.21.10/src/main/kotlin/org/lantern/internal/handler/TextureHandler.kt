package org.lantern.internal.handler

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
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

    fun isHttpUrl(path: String): Boolean = path.startsWith("http://") || path.startsWith("https://")

    fun getTexture(path: String): ResourceLocation = textures[path] ?: run {
        if (isHttpUrl(path)) {
            textures[path] = missingTexture
            downloadResource(path)
            missingTexture
        } else {
            val location = IdentifierBridge.of(Lantern.MOD_ID, path)
            if (Minecraft.getInstance().resourceManager.getResource(location).isPresent) {
                textures[path] = location
                location
            } else {
                missingTexture
            }
        }
    }

    fun downloadResource(path: String) {
        if (!isHttpUrl(path)) return
        CompletableFuture.runAsync {
            try {
                val connection = URI(path).toURL().openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                val bytes = connection.getInputStream().use { it.readAllBytes() }
                if (isGif(bytes)) {
                    val (frames, delays) = decodeGif(bytes) ?: return@runAsync
                    Minecraft.getInstance().execute {
                        val texture = AnimatedGifTexture(frames, delays)
                        val location = nextLocation("gif")
                        Minecraft.getInstance().textureManager.register(location, texture)
                        animatedTextures.add(texture)
                        ownedLocations.add(location)
                        textures[path] = location
                    }
                } else {
                    val image = NativeImage.read(ByteArrayInputStream(bytes))
                    Minecraft.getInstance().execute {
                        val texture = DynamicTexture(Supplier { "Lantern dynamic texture" }, image)
                        val location = nextLocation("image")
                        Minecraft.getInstance().textureManager.register(location, texture)
                        ownedLocations.add(location)
                        textures[path] = location
                    }
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
    }

    private fun nextLocation(type: String): ResourceLocation =
        IdentifierBridge.of(Lantern.MOD_ID, "dynamic/${type}_${idCounter.getAndIncrement()}")

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
                delays.add(delay.coerceAtLeast(100))

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
