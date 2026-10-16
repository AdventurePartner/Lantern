package org.lantern.camera.control

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lantern.core.camera.CameraControl
import org.lantern.core.camera.ShoulderCameraState
import org.lantern.platform.InteractionBridge

/**
 * 准星拾取修正：以**本帧相机位姿**重算方块/实体命中并写回 Minecraft.hitResult。
 *
 * 在 Camera.setup 尾部调用：renderLevel 帧内 pick 先于 Camera.setup，在 pick 尾部
 * 只能读到上一帧的相机——快速转头时准星目标拖后一帧。
 *
 * 生效门控：越肩（F5 第三视角）**或** 演出朝向激活（offset/lock 会旋转任意视角形态
 * 下的视图）。最终仍按玩家眼睛 + 交互距离过滤（复刻原版 filterHitResult），射程语义不变。
 * 1.21.10 有同名副本（camera/control 整包被 NeoForge 排除）。
 */
object CameraPick {

    // 原版 pick 的实体过滤（1.20.1 还没有 EntitySelector.CAN_BE_PICKED）
    private val PICKABLE = java.util.function.Predicate<Entity> { !it.isSpectator && it.isPickable }

    @JvmStatic
    fun recompute(partialTick: Float) {
        val minecraft = Minecraft.getInstance()
        val cameraEntity = minecraft.getCameraEntity() ?: return
        val player = minecraft.player ?: return
        val level = minecraft.level ?: return

        val shoulder = ShoulderCameraState.active &&
            minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK
        if (!shoulder && !CameraControl.orientationActive()) {
            return
        }
        val camera = minecraft.gameRenderer.mainCamera

        val blockRange = InteractionBridge.blockRange(player)
        val entityRange = InteractionBridge.entityRange(player)
        val reach = maxOf(blockRange, entityRange)

        val origin = camera.position
        val look = camera.lookVector
        val direction = Vec3(look.x().toDouble(), look.y().toDouble(), look.z().toDouble())
        var end = origin.add(direction.scale(reach))

        val blockHit = level.clip(
            ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity))
        val blockDistSq = blockHit.location.distanceToSqr(origin)

        var effectiveReachSq = reach * reach
        if (blockHit.type != HitResult.Type.MISS) {
            effectiveReachSq = blockDistSq
            end = origin.add(direction.scale(kotlin.math.sqrt(blockDistSq)))
        }

        val searchBox = AABB(origin, end).inflate(1.0)
        val entityHit = ProjectileUtil.getEntityHitResult(
            cameraEntity, origin, end, searchBox, PICKABLE, effectiveReachSq)

        val eye = player.getEyePosition(partialTick)
        val result = if (entityHit != null && entityHit.location.distanceToSqr(origin) < blockDistSq) {
            filterHitResult(entityHit, eye, entityRange)
        } else {
            filterHitResult(blockHit, eye, blockRange)
        }

        minecraft.hitResult = result
        minecraft.crosshairPickEntity = (result as? EntityHitResult)?.entity
    }

    /** 复刻 GameRenderer.filterHitResult：命中点须在眼睛 + 交互距离内，否则改为 MISS。 */
    private fun filterHitResult(hit: HitResult, eye: Vec3, range: Double): HitResult {
        val location = hit.location
        if (location.closerThan(eye, range)) {
            return hit
        }
        val diff = location.subtract(eye)
        return BlockHitResult.miss(
            location,
            Direction.getNearest(diff.x, diff.y, diff.z),
            BlockPos.containing(location)
        )
    }
}
