package org.lantern.uix.widget

import org.lantern.uix.BaseComponent
import org.lantern.uix.IComponent
import org.lantern.uix.enums.Position

abstract class BaseWidget : BaseComponent(), IWidget {
    private var _parent: IComponent? = null
    private var position = Position.RELATIVE

    override var parent: IComponent?
        get() = _parent
        set(value) {
            _parent = value
        }

    override fun getPosition(): Position {
        return position
    }
}