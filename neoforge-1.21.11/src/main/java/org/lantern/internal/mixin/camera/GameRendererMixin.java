package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lantern.core.camera.CameraControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 演出 FOV（packet 18 fov）：getFov 返回值层叠加，不动 Options.fov。
 * 越肩/演出朝向的拾取修正挂在 pick 尾部（CameraPick）：1.21.11 起帧内先 updateCamera
 * 再 renderLevel → pick，pick 尾部读到的已是本帧相机；挂在 Camera.setup 尾部会被随后的
 * 原版 pick 覆盖。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void lantern$cameraFov(
        Camera camera,
        float partialTick,
        boolean useFovSetting,
        CallbackInfoReturnable<Float> cir
    ) {
        cir.setReturnValue(CameraControl.fov(cir.getReturnValueF()));
    }

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void lantern$recomputePick(float partialTick, CallbackInfo ci) {
        org.lantern.camera.control.CameraPick.recompute(partialTick);
    }
}
