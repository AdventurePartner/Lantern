package org.lantern.internal.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lantern.camera.ShoulderCameraState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 越肩准星修正：原版 pick 以玩家眼睛为射线起点，相机侧偏后准星与实际拾取错位。
 * 在 pick 尾部以相机位置 + 相机视线重算方块/实体命中并写回 Minecraft.hitResult，
 * 最终仍以玩家眼睛 + 交互距离过滤（复刻 filterHitResult），射程语义不变。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void lantern$pickFromShoulderCamera(float partialTick, CallbackInfo ci) {
        if (!ShoulderCameraState.active
            || this.minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            return;
        }
        Camera camera = ((GameRenderer) (Object) this).getMainCamera();
        Entity cameraEntity = this.minecraft.getCameraEntity();
        LocalPlayer player = this.minecraft.player;
        ClientLevel level = this.minecraft.level;
        if (camera == null || cameraEntity == null || player == null || level == null) {
            return;
        }

        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        double reach = Math.max(blockRange, entityRange);

        Vec3 origin = camera.getPosition();
        org.joml.Vector3f look = camera.getLookVector();
        Vec3 direction = new Vec3(look.x(), look.y(), look.z());
        Vec3 end = origin.add(direction.scale(reach));

        HitResult blockHit = level.clip(
            new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity));
        double blockDistSq = blockHit.getLocation().distanceToSqr(origin);

        double effectiveReachSq = reach * reach;
        if (blockHit.getType() != HitResult.Type.MISS) {
            effectiveReachSq = blockDistSq;
            end = origin.add(direction.scale(Math.sqrt(blockDistSq)));
        }

        AABB searchBox = new AABB(origin, end).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            cameraEntity, origin, end, searchBox, EntitySelector.CAN_BE_PICKED, effectiveReachSq);

        Vec3 eye = player.getEyePosition(partialTick);
        HitResult result = entityHit != null && entityHit.getLocation().distanceToSqr(origin) < blockDistSq
            ? lantern$filterHitResult(entityHit, eye, entityRange)
            : lantern$filterHitResult(blockHit, eye, blockRange);

        this.minecraft.hitResult = result;
        this.minecraft.crosshairPickEntity = result instanceof EntityHitResult entityResult
            ? entityResult.getEntity()
            : null;
    }

    @Unique
    private static HitResult lantern$filterHitResult(HitResult hit, Vec3 eye, double range) {
        Vec3 location = hit.getLocation();
        if (location.closerThan(eye, range)) {
            return hit;
        }
        Vec3 diff = location.subtract(eye);
        return BlockHitResult.miss(
            location,
            Direction.getApproximateNearest(diff.x, diff.y, diff.z),
            BlockPos.containing(location)
        );
    }
}
