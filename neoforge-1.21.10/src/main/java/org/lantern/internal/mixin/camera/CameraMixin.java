package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import org.lantern.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 越肩视角：拦截第三人称 detached 分支的 move 调用（Shoulder Surfing 同款结构）。
 * 距离方向先经原版 getMaxZoom 做 8 点防穿墙收缩，再叠加高度与横向偏移；
 * move 轴序为 (前向, 上, 左)，+offsetX 落在左轴即相机移到玩家右肩（offsetX > 0 = 右肩）。
 * 生效条件是 F5 虚拟视角 active（越肩是第四视角，不占用原版背后形态）。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void move(float x, float y, float z);

    @Shadow
    protected abstract float getMaxZoom(float desiredDistance);

    @Redirect(
        method = "setup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;move(FFF)V",
            ordinal = 0
        )
    )
    private void lantern$setupShoulderPosition(
        Camera camera,
        float x,
        float y,
        float z,
        BlockGetter level,
        Entity cameraEntity,
        boolean detached,
        boolean mirrored,
        float partialTick
    ) {
        if (ShoulderCameraState.active && detached && !mirrored
            && !(cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping())) {
            float clipped = this.getMaxZoom(ShoulderCameraState.distance);
            this.move(-clipped, ShoulderCameraState.offsetY, ShoulderCameraState.offsetX);
        } else {
            this.move(x, y, z);
        }
    }
}
