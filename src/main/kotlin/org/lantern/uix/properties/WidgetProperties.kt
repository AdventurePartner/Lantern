package org.lantern.uix.properties

import org.lantern.uix.enums.Position

class WidgetProperties : IProperties {
    private lateinit var position: Position

    companion object {

        fun parse(): WidgetProperties {
            return WidgetProperties()
        }
    }
}