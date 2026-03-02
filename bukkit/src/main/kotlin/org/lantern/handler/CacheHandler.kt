package org.lantern.handler

import org.lantern.cache.ItemIconCache
import org.lantern.cache.KeyCache

object CacheHandler {
    val keys = mutableMapOf<String, KeyCache>()
    val itemIcons = mutableMapOf<Int, ItemIconCache>()
}