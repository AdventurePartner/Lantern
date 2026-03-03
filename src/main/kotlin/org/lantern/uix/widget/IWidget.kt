package org.lantern.uix.widget

import org.lantern.uix.IComponent
import org.lantern.uix.enums.Position
import org.lantern.uix.style.StyleRule

interface IWidget : IComponent {

    var parent: IComponent?

    var style: StyleRule

    /** Widget type identifier, matches the "type" field in config JSON. */
    val widgetType: String

    fun getPosition(): Position
}