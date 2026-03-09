package org.lantern.internal.handler

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.server.packs.resources.ResourceManager

class AnimatedGifTexture(
    private val frames: Array<NativeImage>,
    private val frameDelays: IntArray
) : AbstractTexture() {

    private var currentFrame = -1
    private var startTime = 0L
    private val totalDuration = frameDelays.fold(0L) { acc, d -> acc + d }
    private var prepared = false

    override fun load(resourceManager: ResourceManager) {}

    fun tick() {
        if (frames.isEmpty()) return
        val targetFrame = if (frames.size == 1) {
            0
        } else {
            val now = System.currentTimeMillis()
            if (startTime == 0L) startTime = now
            val elapsed = (now - startTime) % totalDuration
            var acc = 0L
            var target = 0
            for (i in frameDelays.indices) {
                acc += frameDelays[i]
                if (elapsed < acc) {
                    target = i
                    break
                }
            }
            target
        }

        if (targetFrame != currentFrame) {
            currentFrame = targetFrame
            val image = frames[currentFrame]
            val id = this.getId()
            if (!prepared) {
                TextureUtil.prepareImage(id, image.getWidth(), image.getHeight())
                prepared = true
            } else {
                GlStateManager._bindTexture(id)
            }
            image.upload(0, 0, 0, false)
        }
    }

    override fun close() {
        super.close()
        frames.forEach { it.close() }
    }
}
