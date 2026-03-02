package org.lantern.model.enums

/**
 * 实体动画状态枚举
 * 用于定义实体可能处于的各种动画状态
 */
enum class EntityAnimationState {
    IDLE,   // 静止状态
    WALK,   // 行走状态
    ATTACK, // 攻击状态
    HURT,   // 受伤状态
    DEATH   // 死亡状态
}
