package org.lantern.model.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3
import org.lantern.model.enums.EntityAnimationState

/**
 * 实体状态检测工具
 * 用于检测实体当前状态并返回对应的动画状态
 */
object EntityStateUtil {

    // 用于存储上一 tick 的实体位置
    private val lastPositions = mutableMapOf<Int, Vec3>()

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

        // 检查攻击状态（仅对 Mob 类型有效）
        if (entity is Mob && entity.isAggressive) {
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
        val currentPos = entity.position()
        val lastPos = lastPositions[entity.id]

        // 更新位置记录
        lastPositions[entity.id] = currentPos

        // 如果没有上一帧的位置数据，检查实体的移动向量
        if (lastPos == null) {
            return entity.deltaMovement.lengthSqr() > 0.001
        }

        // 计算位移距离
        val distance = currentPos.distanceToSqr(lastPos)

        // 如果位移距离超过阈值，则认为在移动
        return distance > 0.0001
    }

    /**
     * 清理已不存在的实体的位置记录
     */
    fun cleanupEntityPosition(entityId: Int) {
        lastPositions.remove(entityId)
    }
}
