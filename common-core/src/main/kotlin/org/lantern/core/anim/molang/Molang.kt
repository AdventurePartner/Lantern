package org.lantern.core.anim.molang

import com.google.gson.JsonElement

/**
 * 已编译的 molang 表达式。
 *
 * 由各平台的 [MolangCompiler] 实现（GeckoLib 的 MathValue、或未来的自研求值器），
 * eval 在采样线程调用，query.* 与 variable.* 的取值来自 [MolangScope]
 */
fun interface MolangExpression {
    fun eval(scope: MolangScope): Double
}

/**
 * molang 求值上下文：一轮采样（某实体某帧）共享一份。
 *
 * queries 为 query.* 值表（键为 molang 全名，如 "query.anim_time"）；
 * variables 为服务端注入变量已求值的表（键为裸名，与 packet 17 下发的键一致）。
 * revision 在 queries 被覆写时递增——求值引擎据此失效转换缓存
 */
interface MolangScope {
    val queries: Map<String, Double>
    val variables: Map<String, Double>
    val revision: Long
}

/** 可变求值上下文：播放器每帧构造，采样中按层覆写 anim_time 等值 */
class MutableMolangScope : MolangScope {
    override val queries = HashMap<String, Double>(32)
    override var variables: Map<String, Double> = emptyMap()
    private var _revision = 0L
    override val revision: Long get() = _revision

    fun setQuery(name: String, value: Double) {
        queries[name] = value
        _revision++
    }
}

/**
 * molang 编译器：字符串/JSON 值 -> 平台的表达式实现。
 * 编译失败抛异常，由调用方决定告警与降级
 */
interface MolangCompiler {
    /** 编译关键帧值（数值或表达式字符串），数值编译为常量 */
    fun compile(element: JsonElement): MolangExpression

    /** 编译 molang 表达式文本（服务端注入变量的值） */
    fun compileMolang(source: String): MolangExpression
}

/**
 * Lantern 用到的 query 名固化表（值实证自 GeckoLib 5.3-alpha-3 的 MolangQueries）。
 *
 * 固化成字符串常量后 common 侧不依赖 GeckoLib；未来 GeckoLib 4.7 平台接线时
 * 需按 4.7 jar 核对同名常量值是否一致
 */
object QueryNames {
    const val ANIM_TIME = "query.anim_time"
    const val BODY_Y_ROTATION = "query.body_y_rotation"
    const val DAY = "query.day"
    const val DEATH_TICKS = "query.death_ticks"
    const val GROUND_SPEED = "query.ground_speed"
    const val HEAD_X_ROTATION = "query.head_x_rotation"
    const val HEAD_Y_ROTATION = "query.head_y_rotation"
    const val HEALTH = "query.health"
    const val HURT_TIME = "query.hurt_time"
    const val IS_ALIVE = "query.is_alive"
    const val IS_BABY = "query.is_baby"
    const val IS_IN_WATER = "query.is_in_water"
    const val IS_MOVING = "query.is_moving"
    const val IS_ON_GROUND = "query.is_on_ground"
    const val IS_RIDING = "query.is_riding"
    const val IS_SNEAKING = "query.is_sneaking"
    const val IS_SPRINTING = "query.is_sprinting"
    const val LIFE_TIME = "query.life_time"
    const val MAX_HEALTH = "query.max_health"
    const val SCALE = "query.scale"
    const val TIME_OF_DAY = "query.time_of_day"
    const val TIME_STAMP = "query.time_stamp"
    const val VERTICAL_SPEED = "query.vertical_speed"
    const val YAW_SPEED = "query.yaw_speed"

    /** 别名：不少既有资产惯用比 GeckoLib 标准名更短的写法，两套名字都认 */
    const val PITCH = "query.pitch"
    const val YAW = "query.yaw"
}
