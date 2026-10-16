package org.lantern.internal.mixin.accessor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.1 / 1.20.1 的 Level 没有按 UUID 取实体的公开方法，经 getEntities() 走索引查找 */
@Mixin(ClientLevel.class)
public interface ClientLevelEntitiesAccessor {
    @Invoker("getEntities")
    LevelEntityGetter<Entity> lantern$getEntities();
}
