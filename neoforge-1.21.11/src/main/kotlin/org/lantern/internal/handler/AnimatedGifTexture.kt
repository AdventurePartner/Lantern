package org.lantern.internal.handler

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import java.util.function.Supplier

class AnimatedGifTexture(
    private val frames: Array<NativeImage>,
    private val frameDelays: IntArray
) : DynamicTexture(
    Supplier { "Lantern animated texture" },
    NativeImage(frames[0].width, frames[0].height, false)
) {
    private var currentFrame = -1
    private var startTime = 0L
    private val totalDuration = frameDelays.sumOf(Int::toLong)

    fun tick() {
        val targetFrame = if (frames.size == 1) {
            0
        } else {
            val now = System.currentTimeMillis()
            if (startTime == 0L) {
                startTime = now
            }
            val elapsed = (now - startTime) % totalDuration
            var duration = 0L
            frameDelays.indexOfFirst { delay ->
                duration += delay
                elapsed < duration
            }.coerceAtLeast(0)
        }

        if (targetFrame != currentFrame) {
            currentFrame = targetFrame
            pixels!!.copyFrom(frames[currentFrame])
            upload()
        }
    }

    override fun close() {
        super.close()
        frames.forEach(NativeImage::close)
    }
}
