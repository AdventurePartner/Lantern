package org.lantern.ui.layer.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.player.Player
import org.lantern.ui.handler.LayerHandler
import org.lantern.ui.components.IComponent
import org.lantern.ui.components.hud.HudComponent
import org.lantern.ui.layer.BaseLayer
import org.lantern.ui.misc.GeneralMisc

class HudLayerImpl : BaseLayer(GeneralMisc()) {
    private lateinit var minecraft: Minecraft

    init {
//        val params = ImageMisc()
//        params.uniqueId = UUID.randomUUID().toString()
//        params.x = 100
//        params.y = 100
//        params.width = 100
//        params.height = 100
//        params.background = "lantern:textures/example/hud.jpg"
//        addComponent(HudComponent(params))
    }

    override fun addComponent(component: IComponent) {
        check(component is HudComponent, { "Cannot add non-HudComponent elements to HudLayer" })
        super.addComponent(component)
    }

    fun render(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        minecraft = Minecraft.getInstance()
        if (shouldDrawSurvivalElements()) {
            LayerHandler.hudLayer.onComponentsRender(graphics, partialTick, screenWidth, screenHeight)
        }
    }

    fun shouldDrawSurvivalElements(): Boolean {
        return minecraft.gameMode?.canHurtPlayer() == true && minecraft.getCameraEntity() is Player;
    }

    override fun getUniqueId(): String {
        return "hud_layer"
    }
}