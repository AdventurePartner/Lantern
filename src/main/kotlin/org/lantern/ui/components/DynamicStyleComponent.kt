package org.lantern.ui.components

import net.minecraft.client.gui.GuiGraphics
import org.lantern.ui.IElement
import org.lantern.ui.actions.IAction
import org.lantern.ui.misc.GeneralMisc
import org.lantern.ui.styles.IStyle
import org.lantern.ui.styles.enums.PositionType
import org.lantern.ui.styles.impl.GeneralStyleImpl

abstract class DynamicStyleComponent(override val params: GeneralMisc) : IComponent {
    private var style: IStyle = GeneralStyleImpl()
    private var parent: IElement? = null
    private val childs = mutableListOf<IComponent>()

    override fun onRender(graphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        childs.forEach { it.onRender(graphics, partialTick, screenWidth, screenHeight) }
    }

    override fun setStyle(style: IStyle) {
        this.style = style
    }

    override fun addAction(action: IAction) {
    }

    override fun setX(x: Int) {
        params.x = x
    }

    override fun getX(): Int {
        if (style.getPosition() == PositionType.ABSOLUTE) {
            return params.x
        }
        return parent?.getX()?.plus(params.x) ?: params.x
    }

    override fun setY(y: Int) {
        params.y = y
    }

    override fun getY(): Int {
        if (style.getPosition() == PositionType.ABSOLUTE) {
            return params.y
        }
        return parent?.getY()?.plus(params.y) ?: params.y
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
        if (element is IComponent && !childs.contains(element) && childs.add(element)) {
            element.setParent(this)
        }
    }

    override fun getUniqueId(): String {
        return params.uniqueId
    }
}