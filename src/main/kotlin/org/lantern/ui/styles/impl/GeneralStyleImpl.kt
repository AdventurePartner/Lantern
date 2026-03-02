package org.lantern.ui.styles.impl

import org.lantern.ui.styles.IStyle
import org.lantern.ui.styles.enums.PositionType

class GeneralStyleImpl : IStyle {
    private var position = PositionType.RELATIVE

    override fun getPosition(): PositionType {
        return position
    }
}