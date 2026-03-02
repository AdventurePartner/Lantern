package org.lantern.ui.mixed.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import org.lantern.ui.layer.ILayer
import org.lantern.ui.layer.gui.InventoryLayerImpl
import org.lantern.ui.mixed.IMixed
import org.lantern.ui.misc.impl.GuiMisc

class PlayerInventoryMixed(private val layer: InventoryLayerImpl, player: Player) : IMixed, InventoryScreen(player) {
    private val params = layer.getMisc() as GuiMisc
    private val resource = params.background?.let { ResourceLocation.parse(it) }
    private var tempMouseX = 0
    private var tempMouseY = 0

    init {
        this.resource?.let {
            this.imageWidth = params.width
            this.imageHeight = params.height
        }
    }

    override fun getLayer(): ILayer {
        return layer
    }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(arg, i, j, f)
        this.tempMouseX = i
        this.tempMouseY = j
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        this.updateMisc()
        // 判断是否有背景图参数, 如果没有则执行默认背景图渲染
        if (resource != null) {
            this.minecraft?.player?.let {
                val i = this.leftPos;
                val j = this.topPos;
                graphics.blit(resource, i, j, 0F, 0F, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
                    graphics,
                    (i + 51).toFloat(),
                    (j + 75).toFloat(),
                    30f,
                    org.joml.Vector3f(0f, 0f, 0f),
                    org.joml.Quaternionf().rotationXYZ(
                        ((i + 51) - tempMouseX).toFloat() * 0.05f,
                        ((j + 75 - 50) - tempMouseY).toFloat() * 0.05f,
                        0f
                    ),
                    null,
                    it
                );
            }
        } else {
            super.renderBg(graphics, partialTick, screenWidth, screenHeight)
        }
        // 从图层根节点开始渲染组件
        this.layer.onComponentsRender(graphics, partialTick, screenWidth, screenHeight)
    }

    private fun updateMisc() {
        this.layer.setX(this.leftPos)
        this.layer.setY(this.topPos)
    }
}