package org.lantern.model

import net.minecraft.resources.Identifier
import org.lantern.platform.IdentifierBridge

object GeckoResourceIds {
    @JvmStatic
    fun model(location: Identifier): Identifier = IdentifierBridge.of(
        location.namespace,
        location.path
            .removePrefix("geckolib/models/")
            .removePrefix("geo/")
            .removeSuffix(".geo.json")
    )

    @JvmStatic
    fun animation(location: Identifier): Identifier = IdentifierBridge.of(
        location.namespace,
        location.path
            .removePrefix("geckolib/animations/")
            .removePrefix("animations/")
            .removeSuffix(".animation.json")
    )
}
