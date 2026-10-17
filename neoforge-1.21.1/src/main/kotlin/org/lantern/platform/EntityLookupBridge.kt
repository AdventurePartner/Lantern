package org.lantern.platform

import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import org.lantern.internal.mixin.accessor.ClientLevelEntitiesAccessor

object EntityLookupBridge {
    @JvmStatic
    fun byUuid(uuid: UUID): Entity? {
        val level = Minecraft.getInstance().level ?: return null
        return (level as ClientLevelEntitiesAccessor).`lantern$getEntities`().get(uuid)
    }
}
