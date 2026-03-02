package org.lantern.ui.layer

import net.minecraft.client.gui.GuiGraphics
import org.lantern.ui.IElement
import org.lantern.ui.components.IComponent
import org.lantern.ui.misc.GeneralMisc

interface ILayer : IElement {

    fun addComponent(component: IComponent)

    fun onComponentsRender(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int)

    /**
     * 获取混合对象，混合对象是用于存储临时样式数据的类。
     */
    fun getMisc(): GeneralMisc
}