package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.lantern.core.camera.CameraControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
