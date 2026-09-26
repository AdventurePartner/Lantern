package org.lantern.core.anim

/**
 * 使用物品的动作类别（镜像原版 ItemUseAnimation 中判定链用到的子集）。
 * 平台采集时由 ItemUseAction 映射，common 侧不依赖原版枚举
 */
enum class UseActionKind { NONE, BOW, CROSSBOW, BLOCK, EAT, DRINK }

/**
 * 一帧的实体状态快照：播放器判定链（层选片/边沿/姿态/运动实测/molang query）
 * 对实体的一切读取收拢在此，由平台每帧采集一次传入。
 *
 * [living] 为 false（非 LivingEntity，如装饰实体）时：死亡/边沿/姿态/移动判定
 * 全部跳过——与原先 living == null 的行为一致；living 相关字段填 0/false 即可
 */
class ActorSnapshot(
    // 运动实测（位移判定 + jump/landing 边沿）
    val x: Double,
    val y: Double,
    val z: Double,
    /** 物理 onGround：本地每 tick 更新、远程由移动包同步，作为「退出空中」的信号 */
    val physicallyGrounded: Boolean,
    // 空中判定停用（攀爬的实测垂直速度会越过起跳/跌落阈值；飞行同理更甚）
    val climbing: Boolean,
    val flying: Boolean,
    // 基本状态
    val living: Boolean,
    val dead: Boolean,
    // locomotion 判定
    val inWater: Boolean,
    val passenger: Boolean,
    val sneaking: Boolean,
    val sprinting: Boolean,
    // 攻击边沿（swinging 上升沿 + attackAnim 锯齿回落）
    val swinging: Boolean,
    val attackAnim: Float,
    // 上身姿态链
    val usingItem: Boolean,
    /** 正在使用物品的手是否为主手 */
    val usingMainHand: Boolean,
    val useAction: UseActionKind,
    /** 主手物品映射后的 hold_* 名（剑="sword" 等）；null = 无持物姿态 */
    val mainHandHold: String?,
    // molang query 值（living 块内的字段仅在 living=true 时被消费）
    val tickCount: Int,
    val gameTime: Long,
    val dayTime: Long,
    val alive: Boolean,
    val health: Float,
    val maxHealth: Float,
    val hurtTime: Int,
    val baby: Boolean,
    val scale: Float,
    val pitch: Float,
    val yaw: Float,
    val bodyYaw: Float,
    /** 本帧 yaw 与上一帧 yaw 之差（度） */
    val yawSpeed: Float
)
