package org.lantern.uix

import net.minecraft.client.gui.GuiGraphics
import org.lantern.uix.enums.MountPoint
import org.lantern.uix.event.MouseEvent

abstract class BaseComponent : IComponent {
    // 当前挂载点坐标, 注意此处非实时
    private val mount = Array(9) { intArrayOf(0, 0) }
    private var _width = 0
    private var _height = 0
    private var _x = 0
    private var _y = 0
    private var lastWidth = 0
    private var lastHeight = 0

    override var hovered: Boolean = false

    private val clickHandlers = mutableListOf<(MouseEvent) -> Unit>()
    private val enterHandlers = mutableListOf<(MouseEvent) -> Unit>()
    private val leaveHandlers = mutableListOf<(MouseEvent) -> Unit>()

    override var x: Int
        get() = _x
        set(value) {
            _x = value
        }
    override var y: Int
        get() = _y
        set(value) {
            _y = value
        }
    override var width: Int
        get() = _width
        set(value) {
            _width = value
        }
    override var height: Int
        get() = _height
        set(value) {
            _height = value
        }

    override fun onClick(handler: (MouseEvent) -> Unit) {
        clickHandlers.add(handler)
    }

    override fun onMouseEnter(handler: (MouseEvent) -> Unit) {
        enterHandlers.add(handler)
    }

    override fun onMouseLeave(handler: (MouseEvent) -> Unit) {
        leaveHandlers.add(handler)
    }

    override fun dispatchClick(event: MouseEvent) {
        for (handler in clickHandlers) {
            handler(event)
            if (event.consumed) return
        }
    }

    override fun dispatchMouseEnter(event: MouseEvent) {
        hovered = true
        for (handler in enterHandlers) {
            handler(event)
            if (event.consumed) return
        }
    }

    override fun dispatchMouseLeave(event: MouseEvent) {
        hovered = false
        for (handler in leaveHandlers) {
            handler(event)
            if (event.consumed) return
        }
    }

    override fun getMount(point: MountPoint): IntArray {
        return mount[point.index]
    }

    override fun render(arg: GuiGraphics, i: Int, j: Int, f: Float) {
        if (_width != lastWidth || _height != lastHeight) {
            computeMount()
        }
    }

    override fun computeMount() {
        val w2 = _width / 2
        val h2 = _height / 2

        mount[MountPoint.MOUNT_1_FRONT.index] = intArrayOf(0, 0)
        mount[MountPoint.MOUNT_2_FRONT.index] = intArrayOf(0, h2)
        mount[MountPoint.MOUNT_3_FRONT.index] = intArrayOf(0, _height)

        mount[MountPoint.MOUNT_1_MIDDLE.index] = intArrayOf(w2, 0)
        mount[MountPoint.MOUNT_2_MIDDLE.index] = intArrayOf(w2, h2)
        mount[MountPoint.MOUNT_3_MIDDLE.index] = intArrayOf(w2, _height)

        mount[MountPoint.MOUNT_1_BACK.index] = intArrayOf(_width, 0)
        mount[MountPoint.MOUNT_2_BACK.index] = intArrayOf(_width, h2)
        mount[MountPoint.MOUNT_3_BACK.index] = intArrayOf(_width, _height)

        lastWidth = _width
        lastHeight = _height
    }
}