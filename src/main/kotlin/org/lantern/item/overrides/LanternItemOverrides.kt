package org.lantern.item.overrides

import net.minecraft.client.renderer.block.model.ItemOverrides

/**
 * 1.21.1 starts restricting direct ItemOverrides subclassing.
 * Keep a single access point so custom override logic can be reintroduced later.
 */
object LanternItemOverrides {
    val INSTANCE: ItemOverrides = ItemOverrides.EMPTY
}