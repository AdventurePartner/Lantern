package org.lantern.ui.misc

open class GeneralMisc {
    lateinit var uniqueId: String
    var x = 0
    var y = 0
    var width = 0
    var height = 0
    var onPress: (() -> Unit)? = null
}