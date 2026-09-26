package org.lantern.internal.mixin.accessor;

import net.minecraft.world.inventory.Slot;
import org.lantern.uix.renderer.SlotMutableBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 本模块不编译共享 src/main/java，此文件为共享 SlotAccessor 的本地副本。
 */
@Mixin(Slot.class)
public interface SlotAccessor extends SlotMutableBridge {
    @Override
    @Accessor("x")
    @Mutable
    void lantern$setSlotX(int value);

    @Override
    @Accessor("y")
    @Mutable
    void lantern$setSlotY(int value);
}
