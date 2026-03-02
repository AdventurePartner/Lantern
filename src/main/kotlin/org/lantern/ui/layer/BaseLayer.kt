package org.lantern.ui.layer

import net.minecraft.client.gui.GuiGraphics
import org.lantern.ui.IElement
import org.lantern.ui.layer.container.ComponentContainer
import org.lantern.ui.components.IComponent
import org.lantern.ui.misc.GeneralMisc

abstract class BaseLayer(private val params: GeneralMisc) : ILayer {
    private var parent: IElement? = null
    private val childs = mutableListOf<IElement>()
    private val componentContainer = ComponentContainer()

    override fun onComponentsRender(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        this.componentContainer.findAll().forEach {
            it.value.onRender(graphics, partialTick, screenWidth, screenHeight)
        }
        this.getChilds().forEach { (it as IComponent).onRender(graphics, partialTick, screenWidth, screenHeight) }
    }

    override fun addComponent(component: IComponent) {
        componentContainer.addComponent(this, component)
    }

    override fun getMisc(): GeneralMisc {
        return params
    }

    override fun setX(x: Int) {
        params.x = x
    }

    override fun getX(): Int {
        return params.x
    }

    override fun setY(y: Int) {
        params.y = y
    }

    override fun getY(): Int {
        return params.y
    }

    override fun setWidth(width: Int) {
        params.width = width
    }

    override fun getWidth(): Int {
        return params.width
    }

    override fun setHeight(height: Int) {
        params.height = height
    }

    override fun getHeight(): Int {
        return params.height
    }

    override fun getParent(): IElement? {
        return parent
    }

    override fun setParent(element: IElement) {
        parent = element
        element.addChild(this)
    }

    override fun getChilds(): List<IElement> {
        return childs
    }

    override fun addChild(element: IElement) {
        if (!childs.contains(element) && childs.add(element)) {
            element.setParent(this)
        }
    }
}