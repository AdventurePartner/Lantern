package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.lantern.camera.control.CameraPick;
import org.lantern.camera.control.CameraRoll;
import org.lantern.core.camera.CameraControl;
import org.lantern.core.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 共享 CameraMixin 的 1.20.1 版：move / getMaxZoom 收 double。
 * 1.20.1 的视图由 ComputeCameraAngles 事件的 yaw/pitch/roll 经 PoseStack 拼出，
 * 翻滚角只写进 CameraRoll，由 ForgeClientListener 加到事件上
 */
@Mixin(Camera.class)
public abstract class Camera1201Mixin {

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void move(double x, double y, double z);

    @Shadow
    protected abstract double getMaxZoom(double desiredDistance);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract Vec3 getPosition();

    @Redirect(
        method = "setup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;move(DDD)V",
            ordinal = 0
        )
    )
    private void lantern$setupShoulderPosition(
        Camera camera,
        double x,
        double y,
        double z,
        BlockGetter level,
        Entity cameraEntity,
        boolean detached,
        boolean mirrored,
        float partialTick
    ) {
        if (ShoulderCameraState.active && detached && !mirrored && !lantern$isSpectator()
            && !(cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping())) {
            double clipped = this.getMaxZoom(ShoulderCameraState.distance);
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
        CameraRoll.current = 0.0F;
        if (cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping()) {
            return;
        }
        Vec3 position = this.getPosition();
        CameraControl.Pose pose = CameraControl.apply(
            this.yRot, this.xRot, partialTick, position.x, position.y, position.z);
        if (pose != null) {
            this.setRotation(pose.getYaw(), pose.getPitch());
            CameraRoll.current = pose.getRoll();
            org.lantern.core.camera.Position base = pose.getAbsolutePos() != null
                ? pose.getAbsolutePos() : new org.lantern.core.camera.Position(position.x, position.y, position.z);
            this.setPosition(new Vec3(
                base.getX() + pose.getDx(), base.getY() + pose.getDy(), base.getZ() + pose.getDz()));
        }
        CameraPick.recompute(partialTick);
    }

    @Unique
    private static boolean lantern$isSpectator() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.isSpectator();
    }
}
