package org.lantern.internal.mixin.input;

import net.minecraft.client.Minecraft;
import org.lantern.core.input.InputLockStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 输入锁：攻击键与使用键。Fabric 1.21.1、Forge 1.21.1、Forge 1.20.1 共用。
 *
 * 在 startAttack 头部拦下而不是只跳过结算：原版在它内部挥手，挥手照发的话
 * swinging 上升沿一到，连招照样起。持续挖掘（continueAttack）改成“松开”分支，
 * 由原版自己 stopDestroyBlock，不会留下卡住的挖掘进度
 */
@Mixin(Minecraft.class)
public abstract class MinecraftInputLockMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void lantern$lockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (InputLockStore.isLocked("attack")) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean lantern$lockContinueAttack(boolean leftClick) {
        return leftClick && !InputLockStore.isLocked("attack");
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void lantern$lockUse(CallbackInfo ci) {
        if (InputLockStore.isLocked("use")) {
            ci.cancel();
        }
    }
}
