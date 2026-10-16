package org.lantern.internal.mixin.input;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.lantern.core.input.InputLockStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 输入锁（packet 21）：移动 / 跳跃 / 潜行。Fabric 1.21.1、Forge 1.21.1、Forge 1.20.1 共用
 * （1.21.10 的 Input 改成了 record，NeoForge 走 MovementInputUpdateEvent）。
 *
 * 方向键位和 impulse 必须一起清：前者决定冲刺与潜行判定，后者才是 LocalPlayer.aiStep
 * 真正拿去算位移的量，只清一个的话锁形同虚设
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void lantern$applyInputLocks(boolean isSneaking, float sneakSpeedModifier, CallbackInfo ci) {
        Set<String> locks = InputLockStore.activeLocks();
        if (locks.isEmpty()) return;
        Input input = (Input) (Object) this;
        if (locks.contains("move")) {
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
        }
        if (locks.contains("jump")) {
            input.jumping = false;
        }
        if (locks.contains("sneak")) {
            input.shiftKeyDown = false;
        }
    }
}
