package org.lantern.camera.control

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lantern.camera.ShoulderCameraState

/**
 * 准星拾取修正：以**本帧相机位姿**重算方块/实体命中并写回 Minecraft.hitResult。
 *
 * 为什么在 Camera.setup 尾部调用而不是 GameRenderer.pick 尾部：renderLevel 帧内顺序是
 * pick(先) → Camera.setup(后)（1.21.10 字节码 offset 44 vs 175），在 pick 尾部只能读到
 * 上一帧的相机——快速转头时准星目标拖后一帧；setup 尾部位姿已定稿且下游消费者
 * （方块描边/HUD/下一 tick 交互）都在其后读取。
 *
 * 生效门控：越肩（F5 第三视角）**或** 演出朝向状态激活（offset/lock 会旋转任意视角形态
 * 下的视图，一人称同样需要射线跟准星）。最终仍按玩家眼睛 + 交互距离过滤（复刻原版
 * filterHitResult），射程语义不变。
 */
object CameraPick {

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

        val blockRange = player.blockInteractionRange()
        val entityRange = player.entityInteractionRange()
        val reach = maxOf(blockRange, entityRange)

        val origin = camera.position()
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
            cameraEntity, origin, end, searchBox, EntitySelector.CAN_BE_PICKED, effectiveReachSq)

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
            Direction.getApproximateNearest(diff.x, diff.y, diff.z),
            BlockPos.containing(location)
        )
    }
}
