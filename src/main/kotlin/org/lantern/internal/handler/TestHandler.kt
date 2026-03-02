package org.lantern.internal.handler

import org.lantern.Lantern

object TestHandler {
    private val debug = false

    fun debug(message: String) {
        if (debug) Lantern.logger.info(message)
    }
}