package org.lantern.platform

import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity

object EntityLookupBridge {
    @JvmStatic
    fun byUuid(uuid: UUID): Entity? = Minecraft.getInstance().level?.getEntity(uuid)
}
