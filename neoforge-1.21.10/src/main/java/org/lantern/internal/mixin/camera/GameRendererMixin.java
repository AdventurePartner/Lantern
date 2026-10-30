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
 * 越肩/演出朝向的拾取修正（CameraPick）挂两处：pick 尾部管 Minecraft.tick 里
 * handleKeybinds 之前那次 pick(1.0F)（交互读的是它），CameraMixin setup 尾部管渲染帧
 * （帧内 pick 先于 Camera.setup，pick 尾部只能读到上一帧相机）。
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
