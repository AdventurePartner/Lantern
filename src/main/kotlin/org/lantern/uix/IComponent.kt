package org.lantern.uix

import net.minecraft.client.gui.components.Renderable
import org.lantern.uix.enums.MountPoint
import org.lantern.uix.event.MouseEvent

interface IComponent : Renderable {

    var x: Int

    var y: Int

    var width: Int

    var height: Int

    var hovered: Boolean

    fun computeMount()

    fun getMount(point: MountPoint): IntArray

    fun getChildren(): List<IComponent> = emptyList()

    fun onClick(handler: (MouseEvent) -> Unit)

    fun onMouseEnter(handler: (MouseEvent) -> Unit)

    fun onMouseLeave(handler: (MouseEvent) -> Unit)

    fun dispatchClick(event: MouseEvent)

    fun dispatchMouseEnter(event: MouseEvent)

    fun dispatchMouseLeave(event: MouseEvent)
}