package org.lantern.ui.components

import net.minecraft.client.gui.GuiGraphics
import org.lantern.ui.IElement
import org.lantern.ui.actions.IAction
import org.lantern.ui.misc.GeneralMisc
import org.lantern.ui.styles.IStyle

interface IComponent : IElement {
    val params: GeneralMisc

    fun onRender(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int)

    fun setStyle(style: IStyle)

    fun addAction(action: IAction)
}