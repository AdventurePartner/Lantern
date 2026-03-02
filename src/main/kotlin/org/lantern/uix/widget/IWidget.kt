package org.lantern.uix.widget

import org.lantern.uix.IComponent
import org.lantern.uix.enums.Position

interface IWidget : IComponent {

    var parent: IComponent?

    fun getPosition(): Position
}