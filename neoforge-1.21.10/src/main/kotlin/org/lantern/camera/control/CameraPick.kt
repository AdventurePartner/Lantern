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
import org.lantern.core.camera.CameraControl
import org.lantern.core.camera.ShoulderCameraState

/**
 * 准星拾取修正：以**相机位姿**重算方块/实体命中并写回 Minecraft.hitResult。
 *
 * 挂两处：
 * - GameRenderer.pick 尾部：Minecraft.tick 在 handleKeybinds 之前调 pick(1.0F)，攻击/使用/
 *   挖掘读的是这次结果，不在这里重算就是原版眼睛射线；此时相机仍是上一帧渲染的位姿，
 *   正是玩家点击时看到的画面
 * - Camera.setup 尾部：renderLevel 帧内顺序是 pick(先) → Camera.setup(后)（字节码
 *   offset 44 vs 175），在 pick 尾部只能读到上一帧的相机——快速转头时描边拖后一帧
 *
 * 射线起点推到过眼睛、垂直视线的平面：越肩相机在身后数格，从相机量射程会够不到眼前的
 * 目标，还会选中相机与玩家之间的东西。
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

        val eye = player.getEyePosition(partialTick)
        val cameraPos = camera.position()
        val look = camera.lookVector
        val direction = Vec3(look.x().toDouble(), look.y().toDouble(), look.z().toDouble())
        // 起点推到眼睛平面；离眼睛不超过 reach 的射线点必在 origin + reach 以内（横向偏移只会缩短这段）
        val origin = cameraPos.add(direction.scale(maxOf(0.0, eye.subtract(cameraPos).dot(direction))))
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
