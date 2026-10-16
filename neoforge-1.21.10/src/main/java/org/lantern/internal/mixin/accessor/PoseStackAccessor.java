package org.lantern.internal.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.10 的 PoseStack 改成定长列表 + lastIndex，深度直接读这个下标 */
@Mixin(PoseStack.class)
public interface PoseStackAccessor {
    @Accessor("lastIndex")
    int lantern$getLastIndex();
}
