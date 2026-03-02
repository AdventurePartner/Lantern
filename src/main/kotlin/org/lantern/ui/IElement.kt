package org.lantern.ui

interface IElement {

    fun setX(x: Int)

    fun getX(): Int

    fun setY(y: Int)

    fun getY(): Int

    fun setHeight(height: Int)

    fun getHeight(): Int

    fun setWidth(width: Int)

    fun getWidth(): Int

    fun getParent(): IElement?

    fun setParent(element: IElement)

    fun getChilds(): List<IElement>

    fun addChild(element: IElement)

    fun getUniqueId(): String
}