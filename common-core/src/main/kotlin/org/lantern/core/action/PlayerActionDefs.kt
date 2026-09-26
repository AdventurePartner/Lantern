package org.lantern.core.action

import com.google.gson.JsonObject
import org.lantern.core.CoreLog

/**
 * 一条动作定义。
 *
 * @param keySpec 触发键，与 keys.yml 同一套写法（如 "left_alt"、"ctrl+space"）
 * @param file 动画库文件 id（字符串，裸路径由资源侧补 mod 命名空间），独立于 costume 绑定的动画文件
 * @param directions 方向 -> 动画名；八方向 + none（无方向输入时的原地段）
 * @param toCombatLayer true=上身出招层（与移动并存），false=全身运动层
 * @param uninterruptible 播放期间拒绝被同层新动作顶替（无敌帧动作）
 */
class ActionDef(
    val id: String,
    val keySpec: String,
    val file: String,
    val transitionTicks: Int,
    val exitTicks: Int,
    val speed: Float,
    val cooldownMs: Long,
    val toCombatLayer: Boolean,
    val uninterruptible: Boolean,
    val exclusive: Boolean,
    val allowAirborne: Boolean,
    val requireOnGround: Boolean,
    val distance: Double,
    val dashDurationMs: Long,
    val vertical: Double,
    val suppressAttackMs: Long,
    val invulnerableMs: Long,
    /**
     * true = 本地不播，改上报请求交服务端把关（能量、耐力、冷却这类资源逻辑
     * 由附属在 LanternPlayerActionRequestEvent 里实现）。代价是一个 RTT 的
     * 出招延迟，所以默认是 false——零延迟是这套动作系统的立身之本，不能因为
     * 加了扩展点就整体退化
     */
    val serverChecked: Boolean,
    val directions: Map<String, String>
)

/**
 * 连招定义。段位推进、取消窗口、输入窗口全部在客户端本地判定——
 * 出招延迟在动作游戏里不可接受，服务端往返至少一个来回。
 * 伤害帧仍由服务端的动画轨道挂（animations.yml 的 actions），两边各司其职
 */
class ComboStep(
    val animation: String,
    val cancelAt: Float,
    val windowMs: Long,
    val transitionTicks: Int,
    val exitTicks: Int
)

class ComboDef(
    val id: String,
    /** 限定生效的外观 id；null = 不限定（所有外观回落到它） */
    val costume: String?,
    val file: String,
    val toCombatLayer: Boolean,
    val uninterruptible: Boolean,
    val steps: List<ComboStep>
)

/**
 * 玩家主动动作的定义库（packet 19 下发，按键触发、客户端本地播放）。
 *
 * 为什么本地触发而不是走服务端播控：
 * 1. 零往返延迟——翻滚这类闪避动作对输入响应极敏感，一个来回 100ms 就废了；
 * 2. 不占广播——播控是全服广播，MMO 规模下每次翻滚都乘以在线人数；
 * 3. 方向判定要用玩家的**按键输入**（前/后/左/右的组合），这个量只有本地客户端有，
 *    服务端拿到的只有移动结果，斜向翻滚分不出来。
 *
 * 触发、位移施加与上报（读本地玩家输入/物理）留在平台侧 PlayerActionStore。
 */
object PlayerActionDefs {

    @Volatile
    private var attackCombos: List<ComboDef> = emptyList()

    @Volatile
    private var definitions: List<ActionDef> = emptyList()

    /**
     * 取该外观下生效的连招：外观限定优先，没有则回落到不限定的那条。
     * costumeId 为 null（非 hostDriven 玩家、普通生物）时不启用连招——
     * 连招是玩家功能，实体走自己的状态表
     */
    fun attackCombo(costumeId: String?): ComboDef? {
        if (costumeId == null || attackCombos.isEmpty()) return null
        return attackCombos.firstOrNull { it.costume == costumeId }
            ?: attackCombos.firstOrNull { it.costume == null }
    }

    fun definitions(): List<ActionDef> = definitions

    fun clear() {
        definitions = emptyList()
        attackCombos = emptyList()
    }

    /** packet 19：解析服务端下发的动作定义表 */
    fun load(payload: JsonObject) {
        val array = payload.getAsJsonArray("actions") ?: run {
            definitions = emptyList()
            return
        }
        val combos = ArrayList<ComboDef>()
        val parsed = ArrayList<ActionDef>(array.size())
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val id = obj.get("id")?.asString ?: continue
            val file = obj.get("file")?.asString?.takeIf { it.isNotBlank() } ?: continue

            // 连招条目：有 steps 即为连招，触发源 attack 走挥击边沿
            val stepsArray = obj.getAsJsonArray("steps")
            if (stepsArray != null && stepsArray.size() > 0) {
                val steps = ArrayList<ComboStep>(stepsArray.size())
                for (stepElement in stepsArray) {
                    val so = stepElement as? JsonObject ?: continue
                    val animation = so.get("animation")?.asString?.takeIf { it.isNotBlank() } ?: continue
                    steps.add(
                        ComboStep(
                            animation = animation,
                            cancelAt = (so.get("cancel-at")?.asFloat ?: 0.7f).coerceIn(0f, 1f),
                            windowMs = so.get("window")?.asLong ?: 600L,
                            transitionTicks = so.get("transition")?.asInt ?: 2,
                            exitTicks = so.get("exit-transition")?.asInt ?: 3
                        )
                    )
                }
                if (steps.isNotEmpty() &&
                    (obj.get("trigger")?.asString ?: "key").equals("attack", ignoreCase = true)
                ) {
                    combos.add(ComboDef(
                        id = id,
                        costume = obj.get("costume")?.asString?.takeIf { it.isNotBlank() },
                        file = file,
                        toCombatLayer = !obj.get("layer")?.asString.equals("motion", ignoreCase = true),
                        uninterruptible = obj.get("uninterruptible")?.asBoolean ?: false,
                        steps = steps
                    ))
                }
                continue
            }

            // 方向动作才需要触发键；连招靠挥击边沿触发，没有 key 字段——
            // 这个取值原先在 steps 解析之前，把所有连招条目都提前 continue 掉了
            val keySpec = obj.get("key")?.asString?.takeIf { it.isNotBlank() } ?: continue
            val directions = LinkedHashMap<String, String>()
            obj.getAsJsonObject("directions")?.entrySet()?.forEach { (key, value) ->
                if (value.isJsonPrimitive) directions[key.lowercase()] = value.asString
            }
            if (directions.isEmpty()) continue

            parsed.add(
                ActionDef(
                    id = id,
                    keySpec = keySpec,
                    file = file,
                    transitionTicks = obj.get("transition")?.asInt ?: 2,
                    exitTicks = obj.get("exit-transition")?.asInt ?: 3,
                    speed = obj.get("speed")?.asFloat ?: 1.0f,
                    cooldownMs = obj.get("cooldown")?.asLong ?: 0L,
                    toCombatLayer = obj.get("layer")?.asString.equals("combat", ignoreCase = true),
                    uninterruptible = obj.get("uninterruptible")?.asBoolean ?: false,
                    exclusive = obj.get("exclusive")?.asBoolean ?: false,
                    allowAirborne = obj.get("airborne")?.asBoolean ?: true,
                    requireOnGround = obj.get("require-ground")?.asBoolean ?: false,
                    distance = obj.get("distance")?.asDouble ?: 0.0,
                    dashDurationMs = obj.get("dash-duration")?.asLong ?: 0L,
                    vertical = obj.get("vertical")?.asDouble ?: 0.0,
                    suppressAttackMs = obj.get("suppress-vanilla-attack")?.asLong ?: 0L,
                    invulnerableMs = obj.get("invulnerable")?.asLong ?: 0L,
                    serverChecked = obj.get("server-checked")?.asBoolean ?: false,
                    directions = directions
                )
            )
        }
        definitions = parsed
        // 外观限定的排前面，查找时先精确匹配再回落不限定的那条
        attackCombos = combos.sortedByDescending { if (it.costume != null) 1 else 0 }
        CoreLog.logger.info(
            "[Lantern] Loaded ${parsed.size} player action(s), ${attackCombos.size} combo set(s)"
        )
    }

    /** 方向 -> 动画名解析：配了斜向就用斜向，没配回落到相邻主方向，再回落 none——
     *  资产只有四段前后左右时，斜向自动走最接近的那段 */
    fun resolveAnimation(def: ActionDef, direction: String): String {
        val fallback = when (direction) {
            "forward_left", "forward_right" -> "forward"
            "backward_left", "backward_right" -> "backward"
            else -> "none"
        }
        return def.directions[direction]
            ?: def.directions[fallback]
            ?: def.directions["none"]
            ?: def.directions.values.first()
    }
}
