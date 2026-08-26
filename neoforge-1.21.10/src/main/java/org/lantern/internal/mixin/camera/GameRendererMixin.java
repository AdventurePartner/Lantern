package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lantern.camera.control.CameraControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 演出 FOV（packet 18 fov）：getFov 返回值层叠加，不动 Options.fov。
 * 越肩/演出朝向的拾取修正已迁至 CameraMixin setup 尾部（CameraPick）——
 * 帧内 pick 先于 Camera.setup，在 pick 尾部只能读到上一帧相机。
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
}
