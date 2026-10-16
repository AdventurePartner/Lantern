package org.lantern.internal.mixin.input;

import net.minecraft.client.MouseHandler;
import org.lantern.core.input.InputLockStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * turn 输入锁：锁定期间鼠标不再转动视角（1.21.1；1.20.1 由 MouseHandler1201Mixin 顶替）。
 *
 * 在 turnPlayer 源头拦下，而不是每 tick 把 yRot/xRot 改回去——后者只在 tick
 * 边界纠正，两次 tick 之间视角照样跟着鼠标晃。调用方 handleAccumulatedMovement
 * 在 turnPlayer 之后无条件把 accumulatedDX/DY 清零，取消不会攒下位移
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void lantern$freezeViewWhenLocked(double movementTime, CallbackInfo ci) {
        if (InputLockStore.isLocked("turn")) {
            ci.cancel();
        }
    }
}
