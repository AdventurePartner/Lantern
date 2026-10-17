package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lantern.camera.control.CameraPick;
import org.lantern.core.camera.CameraControl;
import org.lantern.core.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 越肩视角与相机演出（Fabric 1.21.1 / NeoForge 1.21.1 共用；1.20.1 与 1.21.10 各有副本）。
 *
 * 越肩：拦截第三人称 detached 分支的 move 调用，距离先经原版 getMaxZoom 做防穿墙收缩，
 * 再叠加高度与横向偏移；move 轴序为 (前向, 上, 左)，offsetX > 0 = 右肩。
 * 演出：setup 尾部应用 CameraControl 的位姿（offset → lock → path/watch → shake）。
 * 原版 1.21.1 只有两参 setRotation，翻滚角直接写进 rotation 四元数。NeoForge 1.21.1 在
 * setup 开头就发出 ComputeCameraAngles，本注入在其后，写入的翻滚即为最终结果。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    @Final
    private static Vector3f FORWARDS;

    @Shadow
    @Final
    private static Vector3f UP;

    @Shadow
    @Final
    private static Vector3f LEFT;

    @Shadow
    protected abstract void move(float x, float y, float z);

    @Shadow
    protected abstract float getMaxZoom(float desiredDistance);

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
        CameraControl.Pose pose = CameraControl.apply(
            this.yRot, this.xRot, partialTick, position.x, position.y, position.z);
        if (pose != null) {
            this.setRotation(pose.getYaw(), pose.getPitch());
            if (pose.getRoll() != 0.0F) {
                lantern$applyRoll(pose.getYaw(), pose.getPitch(), pose.getRoll());
            }
            // path/watch/回程为绝对位置覆写（shake 扰动照常叠加）；其余为相对扰动
            org.lantern.core.camera.Position base = pose.getAbsolutePos() != null
                ? pose.getAbsolutePos() : new org.lantern.core.camera.Position(position.x, position.y, position.z);
            this.setPosition(new Vec3(
                base.getX() + pose.getDx(), base.getY() + pose.getDy(), base.getZ() + pose.getDz()));
        }
        // 拾取修正必须在 setup 尾部（本帧位姿定稿后）而非 GameRenderer.pick 尾部：
        // 帧内顺序是 pick 在前 setup 在后，后者只能拿到上一帧相机（详见 CameraPick）
        CameraPick.recompute(partialTick);
    }

    /** 与 NeoForge 三参 setRotation 同式：rotationYXZ(π - yaw, -pitch, roll) 后重算三个基向量 */
    @Unique
    private void lantern$applyRoll(float yaw, float pitch, float roll) {
        float rad = (float) (Math.PI / 180.0);
        this.rotation.rotationYXZ((float) Math.PI - yaw * rad, -pitch * rad, roll * rad);
        FORWARDS.rotate(this.rotation, this.forwards);
        UP.rotate(this.rotation, this.up);
        LEFT.rotate(this.rotation, this.left);
    }

    @Unique
    private static boolean lantern$isSpectator() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.isSpectator();
    }
}
