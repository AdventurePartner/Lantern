package org.lantern.model.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.lantern.model.enums.EntityAnimationState

/**
 * 实体状态检测工具
 * 用于检测实体当前状态并返回对应的动画状态
 */
object EntityStateUtil {

    private data class MotionSample(val tick: Int, val pos: Vec3, val moving: Boolean)

    // 按实体 tick 采样：渲染每帧都会调用，按帧比较位置会让同一 tick 内的后几帧读成“没动”，
    // 帧率高于 20 时 walk/idle 来回闪
    private val lastPositions = mutableMapOf<Int, MotionSample>()

    /**
     * 检测实体当前的动画状态
     * 优先级: DEATH > HURT > ATTACK > WALK > IDLE
     */
    fun detectAnimationState(entity: Entity): EntityAnimationState {
        // 检查死亡状态
        if (entity is LivingEntity && entity.isDeadOrDying) {
            return EntityAnimationState.DEATH
        }

        // 检查受伤状态
        if (entity is LivingEntity && entity.hurtTime > 0) {
            return EntityAnimationState.HURT
        }

        // 攻击：Mob 看仇恨状态，玩家看挥手（玩家不是 Mob，外观的攻击动画原先永远不触发）
        if (entity is Mob && entity.isAggressive) {
            return EntityAnimationState.ATTACK
        }
        if (entity is Player && entity.swinging) {
            return EntityAnimationState.ATTACK
        }

        // 检查移动状态
        if (isEntityMoving(entity)) {
            return EntityAnimationState.WALK
        }

        // 默认静止状态
        return EntityAnimationState.IDLE
    }

    /**
     * 检测实体是否在移动
     */
    fun isEntityMoving(entity: Entity): Boolean {
        val last = lastPositions[entity.id]
        if (last != null && last.tick == entity.tickCount) {
            return last.moving
        }
        val currentPos = entity.position()
        val moving = if (last == null) {
            // 没有上一 tick 的位置时看移动向量
            entity.deltaMovement.lengthSqr() > 0.001
        } else {
            currentPos.distanceToSqr(last.pos) > 0.0001
        }
        lastPositions[entity.id] = MotionSample(entity.tickCount, currentPos, moving)
        return moving
    }

    /**
     * 清理已不存在的实体的位置记录
     */
    fun cleanupEntityPosition(entityId: Int) {
        lastPositions.remove(entityId)
    }
}
