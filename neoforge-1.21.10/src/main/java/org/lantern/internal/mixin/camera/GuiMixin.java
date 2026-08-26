package org.lantern.internal.mixin.camera;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Gui;
import org.lantern.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 越肩下补画准星：原版 renderCrosshair 仅一人称渲染，
 * 越肩的射线/交互已按屏幕中心修正（GameRendererMixin），准星同样应显示。
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Redirect(
        method = "renderCrosshair",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"
        )
    )
    private boolean lantern$crosshairInShoulder(CameraType instance) {
        return instance.isFirstPerson() || ShoulderCameraState.active;
    }
}
