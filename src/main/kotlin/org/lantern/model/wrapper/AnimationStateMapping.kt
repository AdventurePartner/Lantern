package org.lantern.model.wrapper

import com.google.gson.JsonObject

/**
 * 状态播放语义。
 * LOOP 回环播放；ONCE 播到末帧后回落（jump/landing/spawn/heal/attack）；
 * HOLD_LAST_FRAME 播到末帧钉住、判定条件消失才回落（pull_bow/hold_* 持物蓄力类）。
 * ONCE 与 HOLD 的时间轴行为相同（钳末帧），区别只在持有方式：时间到期 vs 条件持有
 */
enum class PlayMode { LOOP, ONCE, HOLD_LAST_FRAME }

/**
 * 单个动画状态的播放配置
 *
 * @param animation .animation.json 内的动画名
 * @param playMode 播放语义，缺省 LOOP
 * @param transition 切换到该状态的过渡 tick（客户端 ×50ms 换算），缺省 5 = 0.25s
 */
class StateConfig(
    val animation: String,
    val playMode: PlayMode = PlayMode.LOOP,
    val transition: Int = 5
)

/**
 * 动画状态表：状态名 -> 播放配置（阶段三扩容，替代原 5 字段固定映射）。
 *
 * 状态全集见 docs/animation-control-plan.md §5；表中只有服务端配置了的状态，
 * 未配置的状态在客户端判定链中直接穿透到下一优先级。idle/walk 永远存在。
 *
 * 旧字段访问（idle/walk/attack/hurt/death）保留为派生属性，
 * 兼容旧平台 GeckoLib 控制器管线（GenericReplacedEntity/CostumeAnimatable）
 */
class AnimationStateMapping(private val stateTable: Map<String, StateConfig>) {

    val table: Map<String, StateConfig> get() = stateTable

    val idle: String get() = stateTable["idle"]?.animation ?: "idle"
    val walk: String get() = stateTable["walk"]?.animation ?: "walk"
    val attack: String? get() = stateTable["attack"]?.animation
    val hurt: String? get() = stateTable["hurt"]?.animation
    val death: String? get() = stateTable["death"]?.animation

    fun config(state: String): StateConfig? = stateTable[state]

    companion object {
        /**
         * 创建默认的动画状态映射（只使用 idle）
         */
        fun default(idleAnimation: String = "idle"): AnimationStateMapping {
            return AnimationStateMapping(
                mapOf(
                    "idle" to StateConfig(idleAnimation),
                    "walk" to StateConfig(idleAnimation)
                )
            )
        }

        /**
         * 解析 packet 2 下发的 states JSON 对象为状态表。
         *
         * 值两种形态：string（纯动画名，播放语义 LOOP/5tick——五个旧 key 的下发格式）
         * 或 object {animation, mode: loop|once|hold, transition}（新状态的完整语义，
         * 由服务端解析 yml 时按默认语义表展开，客户端不做二次默认）。
         * null 整体缺省时等价 default()
         */
        fun fromStatesJson(statesObj: JsonObject?): AnimationStateMapping {
            if (statesObj == null) {
                // states 整体缺省：与旧 5 字段解析的缺省一致（walk 缺省是 "walk" 而非 idle）
                return AnimationStateMapping(mapOf(
                    "idle" to StateConfig("idle"),
                    "walk" to StateConfig("walk")
                ))
            }
            val parsed = LinkedHashMap<String, StateConfig>()
            for ((key, value) in statesObj.entrySet()) {
                val config = when {
                    value.isJsonPrimitive -> StateConfig(value.asString)
                    value.isJsonObject -> {
                        val o = value.asJsonObject
                        val animation = o.get("animation")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                        val mode = o.get("mode")?.takeIf { it.isJsonPrimitive }?.asString
                            ?.let { name -> PlayMode.values().firstOrNull { it.name.equals(name, true) } }
                            ?: PlayMode.LOOP
                        StateConfig(animation, mode, o.get("transition")?.takeIf { it.isJsonPrimitive }?.asInt ?: 5)
                    }
                    else -> continue
                }
                parsed[key] = config
            }
            parsed.getOrPut("idle") { StateConfig("idle") }
            parsed.getOrPut("walk") { StateConfig("walk") }
            return AnimationStateMapping(parsed)
        }
    }
}
