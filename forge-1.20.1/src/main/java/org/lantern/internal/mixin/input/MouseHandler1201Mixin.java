package org.lantern.internal.mixin.input;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.MouseHandler;
import org.lantern.core.input.InputLockStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * turn 输入锁的 1.20.1 版：这里的 turnPlayer() 自己负责清零 accumulatedDX/DY 并推进
 * lastMouseEventTime，只取消不清零的话位移一直攒着，解锁那一刻视角会猛甩一下
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandler1201Mixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    private double lastMouseEventTime;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void lantern$freezeViewWhenLocked(CallbackInfo ci) {
        if (InputLockStore.isLocked("turn")) {
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            this.lastMouseEventTime = Blaze3D.getTime();
            ci.cancel();
        }
    }
}
