package org.lantern.internal.mixin.camera;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lantern.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F5 四视角循环：一人称 → 越肩 → 原版第三人称背后 → 正面 → 一人称。
 * 越肩不占用原版第三人称形态——激活时底层 CameraType 保持 THIRD_PERSON_BACK，
 * 由 [ShoulderCameraState.active] 区分「越肩」与「原版背后」。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public LocalPlayer player;

    @Redirect(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"
        )
    )
    private CameraType lantern$cycleWithShoulder(CameraType instance) {
        LocalPlayer player = this.player;
        if (!ShoulderCameraState.enabled || player == null || player.isSpectator()) {
            return instance.cycle();
        }
        if (ShoulderCameraState.active && instance == CameraType.THIRD_PERSON_BACK) {
            // 越肩 -> 原版第三人称背后（类型不变，仅退出虚拟视角）
            ShoulderCameraState.active = false;
            return CameraType.THIRD_PERSON_BACK;
        }
        if (instance == CameraType.FIRST_PERSON) {
            // 一人称 -> 越肩（底层类型切到背后）
            ShoulderCameraState.active = true;
            return CameraType.THIRD_PERSON_BACK;
        }
        // 背后 -> 正面 / 正面 -> 一人称（原版循环；顺带复位虚拟状态）
        ShoulderCameraState.active = false;
        return instance.cycle();
    }
}
