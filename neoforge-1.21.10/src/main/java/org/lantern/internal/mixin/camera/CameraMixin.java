package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.lantern.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 越肩视角：拦截第三人称 detached 分支的 move 调用（Shoulder Surfing 同款结构）。
 * 距离方向先经原版 getMaxZoom 做 8 点防穿墙收缩，再叠加高度与横向偏移；
 * move 轴序为 (前向, 上, 左)，+offsetX 落在左轴即相机移到玩家右肩（offsetX > 0 = 右肩）。
 * 生效条件是 F5 虚拟视角 active（越肩是第四视角，不占用原版背后形态）。
 *
 * setup 尾部叠加演出指令（阶段二）：offset（朝向叠加）→ lock（朝向覆写）→ shake（位置扰动），
 * 计算在 [org.lantern.camera.control.CameraControl]，本类只负责应用。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void move(float x, float y, float z);

    @Shadow
    protected abstract float getMaxZoom(float desiredDistance);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float zRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract Vec3 getPosition();

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
        if (ShoulderCameraState.active && detached && !mirrored && !lantern$isSpectator()
            && !(cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping())) {
            float clipped = this.getMaxZoom(ShoulderCameraState.distance);
            this.move(-clipped, ShoulderCameraState.offsetY, ShoulderCameraState.offsetX);
        } else {
            this.move(x, y, z);
        }
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void lantern$applyCameraControl(
        BlockGetter level,
        Entity cameraEntity,
        boolean detached,
        boolean mirrored,
        float partialTick,
        CallbackInfo ci
    ) {
        // 沉睡实体：原版床视角是刻意摆放的机位，演出覆写不与其打架
        if (cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping()) {
            return;
        }
        Vec3 position = this.getPosition();
        org.lantern.camera.control.CameraControl.Pose pose = org.lantern.camera.control.CameraControl.apply(
            this.yRot, this.xRot, partialTick, position.x, position.y, position.z);
        if (pose != null) {
            this.setRotation(pose.getYaw(), pose.getPitch(), pose.getRoll());
            this.setPosition(new Vec3(
                position.x + pose.getDx(), position.y + pose.getDy(), position.z + pose.getDz()));
        }
        // 拾取修正必须在 setup 尾部（本帧位姿定稿后）而非 GameRenderer.pick 尾部：
        // 帧内顺序是 pick 在前 setup 在后，后者只能拿到上一帧相机（详见 CameraPick）
        org.lantern.camera.control.CameraPick.recompute(partialTick);
    }

    @Unique
    private static boolean lantern$isSpectator() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.isSpectator();
    }
}
