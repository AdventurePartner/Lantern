package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.lantern.camera.control.CameraPick;
import org.lantern.core.camera.CameraControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 演出 FOV（packet 18 fov）：getFov 返回值层叠加，不动 Options.fov。
 * 1.21.1 / 1.20.1 的 getFov 返回 double（1.21.10 改成 float，NeoForge 1.21.10 有自己的副本）；
 * Forge / NeoForge 的 ComputeFov 事件在方法内部触发，RETURN 处已含其结果
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lantern$cameraFov(
        Camera camera,
        float partialTick,
        boolean useFovSetting,
        CallbackInfoReturnable<Double> cir
    ) {
        cir.setReturnValue((double) CameraControl.fov(cir.getReturnValue().floatValue()));
    }

    /**
     * Minecraft.tick 在 handleKeybinds 之前会调 pick(1.0F)，原版眼睛射线结果会盖掉
     * setup 尾部的修正，攻击/使用/挖掘随之按玩家朝向选目标；这里在每次原版 pick 后重算
     */
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void lantern$recomputePick(float partialTick, CallbackInfo ci) {
        CameraPick.recompute(partialTick);
    }
}
