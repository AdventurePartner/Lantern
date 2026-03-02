package org.lantern.uix

import net.minecraft.client.gui.components.Renderable
import org.lantern.uix.enums.MountPoint

interface IComponent : Renderable {

    var x: Int

    var y: Int

    var width: Int

    var height: Int

    fun computeMount()

    fun getMount(point: MountPoint): IntArray
}