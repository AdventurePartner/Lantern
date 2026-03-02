package org.lantern.ui.components.image

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.lantern.ui.components.DynamicStyleComponent
import org.lantern.ui.misc.impl.ImageMisc

open class ImageComponent(params: ImageMisc) : DynamicStyleComponent(params) {
    private val resource = params.background?.let { ResourceLocation.parse(it) }

    override fun onRender(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        if (resource == null) return
        this.renderBackground(graphics)
    }

    private fun renderBackground(graphics: GuiGraphics) {
        checkNotNull(resource, { "Image resource must be not null." })
        RenderSystem.enableBlend()
        graphics.pose().pushPose()
        graphics.blit(
            resource,
            getX(),
            getY(),
            0F,
            0F,
            params.width,
            params.height,
            params.width,
            params.height
        )
        graphics.flush()
        RenderSystem.disableBlend()
        graphics.pose().popPose()
    }
}