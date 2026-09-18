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
 * 特殊状态 head：配了就常驻在叠加层（ADDING），用于「头随视角」。
 * 该动画应当只控 head 骨、用 molang 取视角角度，例如动画 json 里写
 * "head": { "rotation": ["query.pitch", "query.yaw", 0] }，
 * 配置侧则在 states 的 head 下配 animation 与 mode 两字段。
 * 用叠加而非覆盖，走路时资产自带的头部摆动才不会被抹掉。
 *
 * 旧字段访问（idle/walk/attack/hurt/death）保留为派生属性，
 * 兼容旧平台 GeckoLib 控制器管线（GenericReplacedEntity/CostumeAnimatable）
 */
class AnimationStateMapping(
    private val stateTable: Map<String, StateConfig>,
    /**
     * 上身骨骼集：出招层在这些骨骼上满权重，其余（腿部）按移动状态让位给下层。
     * 做成配置是因为骨架命名随资产而变——硬编码在客户端就意味着换一套骨架
     * 就得改代码。缺省值是决策 F 的 15 关节规范，按标准人模命名的资产直接可用。
     * 比较时统一小写（动画轨道的骨骼名也按小写归一）
     */
    val upperBodyBones: Set<String> = DEFAULT_UPPER_BODY_BONES
) {

    val table: Map<String, StateConfig> get() = stateTable

    val idle: String get() = stateTable["idle"]?.animation ?: "idle"
    val walk: String get() = stateTable["walk"]?.animation ?: "walk"
    val attack: String? get() = stateTable["attack"]?.animation
    val hurt: String? get() = stateTable["hurt"]?.animation
    val death: String? get() = stateTable["death"]?.animation

    fun config(state: String): StateConfig? = stateTable[state]

    companion object {
        /** 决策 F 的 15 关节规范里属于上半身的部分；waist 不列入（它是双腿的父骨） */
        val DEFAULT_UPPER_BODY_BONES: Set<String> = setOf(
            "body", "head",
            "rightarm", "rightforearm", "leftarm", "leftforearm",
            "rightitem", "leftitem", "rightshield", "leftshield"
        )

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
         * 或 object（animation、mode（loop|once|hold）、transition 三字段，新状态的完整语义，
         * 由服务端解析 yml 时按默认语义表展开，客户端不做二次默认）。
         * null 整体缺省时等价 default()
         */
        fun fromStatesJson(
            statesObj: JsonObject?,
            upperBodyBonesArray: com.google.gson.JsonArray? = null
        ): AnimationStateMapping {
            val upperBones = upperBodyBonesArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.lowercase() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_UPPER_BODY_BONES

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
                        // playerActions 规范用简名（loop/once/hold），枚举名是
                        // LOOP/ONCE/HOLD_LAST_FRAME——先按简名映射，再按枚举全名匹配
                        val mode = when (o.get("mode")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase()) {
                            "loop" -> PlayMode.LOOP
                            "once" -> PlayMode.ONCE
                            "hold", "hold_last_frame" -> PlayMode.HOLD_LAST_FRAME
                            else -> PlayMode.LOOP
                        }
                        StateConfig(animation, mode, o.get("transition")?.takeIf { it.isJsonPrimitive }?.asInt ?: 5)
                    }
                    else -> continue
                }
                parsed[key] = config
            }
            parsed.getOrPut("idle") { StateConfig("idle") }
            parsed.getOrPut("walk") { StateConfig("walk") }
            return AnimationStateMapping(parsed, upperBones)
        }
    }
}
