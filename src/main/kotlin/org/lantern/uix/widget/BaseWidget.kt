package org.lantern.uix.widget

import org.lantern.uix.BaseComponent
import org.lantern.uix.IComponent
import org.lantern.uix.enums.Position
import org.lantern.uix.style.StyleRule

abstract class BaseWidget : BaseComponent(), IWidget {
    private var _parent: IComponent? = null
    private var _style: StyleRule = StyleRule.EMPTY
    private var position = Position.RELATIVE

    override var parent: IComponent?
        get() = _parent
        set(value) { _parent = value }

    override var style: StyleRule
        get() = _style
        set(value) { _style = value }

    override fun getPosition(): Position = position
}