package org.lantern.cache

import org.bukkit.configuration.ConfigurationSection

/**
 * 玩家主动动作定义（playerActions.yml 一条目）。
 *
 * 触发在客户端本地完成：按键按下即播，不经服务端往返，也不占播控广播。
 * 服务端只负责把定义下发给客户端（packet 19）。
 *
 * 两类条目，靠字段区分：
 *
 * 一、方向动作（有 directions）——按键触发，按移动键组合选段：
 *
 *   翻滚:
 *     key: "left_alt"
 *     file: "animations/player/roll.animation.json"
 *     transition: 2            # 起手过渡 tick
 *     exit-transition: 5       # 收尾过渡 tick
 *     cooldown: 800            # 冷却毫秒
 *     layer: motion            # motion=全身 / combat=上身
 *     exclusive: true          # 播放期间压制出招层
 *     uninterruptible: true    # 拒绝被同层新动作顶替
 *     invulnerable: 500        # 无敌帧毫秒
 *     distance: 4.0            # 位移格数
 *     airborne: true           # 允许空中触发
 *     directions:
 *       forward: "前翻滚"
 *       backward: "后翻滚"
 *       left: "左翻滚"
 *       right: "右翻滚"
 *       forward_left: "前翻滚"   # 斜向没配会自动回落相邻主方向
 *       none: "前翻滚"           # 没按方向键时
 *
 * 二、连招（有 steps）——挥击触发，段位按顺序推进：
 *
 *   连击:
 *     trigger: attack
 *     costume: player_default  # 限定外观；留空则所有外观回落到它
 *     file: "animations/player/player_default.animation.json"
 *     layer: combat
 *     steps:
 *       - animation: "左砍"
 *         cancel-at: 0.7
 *         window: 700
 *       - animation: "右砍"
 *         cancel-at: 0.7
 *         window: 700
 *       - animation: "蓄力砍"
 *         cancel-at: 0.9
 *         window: 800
 *
 * 动画文件路径相对客户端资源包的 assets/lantern/，且只能用 a-z 0-9 _ . - /
 * （资源路径规则），动画名是 json 内部的键、不受此限制。
 */
class PlayerActionCache(section: ConfigurationSection) {

    /** 触发键，与 keys.yml 同一套写法（如 left_alt、ctrl+space） */
    val key: String = section.getString("key", "")!!

    /** 动画库文件（相对客户端资源包的 assets/lantern/，或带命名空间的完整路径） */
    val file: String = section.getString("file", "")!!

    /** 起手过渡 tick */
    val transition: Int = section.getInt("transition", 2).coerceAtLeast(0)

    /** 收尾过渡 tick：动作播完融回行走/跳跃的时长。太短会在末帧硬切出一帧突变 */
    val exitTransition: Int = section.getInt("exit-transition", 3).coerceAtLeast(0)

    /** 播放速度，负值即倒放 */
    val speed: Double = section.getDouble("speed", 1.0)

    /** 冷却毫秒，0 = 不限 */
    val cooldown: Long = section.getLong("cooldown", 0L).coerceAtLeast(0L)

    /** 归层：motion=全身运动层（压住行走）；combat=上身出招层（与移动并存） */
    val layer: String = section.getString("layer", "motion")!!

    /** 霸体：播放期间拒绝被同层新动作顶替 */
    val uninterruptible: Boolean = section.getBoolean("uninterruptible", false)

    /**
     * 出招期间压制原版攻击伤害的毫秒数；0 = 不压制。
     *
     * 动作 RPG 里伤害应当由动画时间线的伤害帧决定（animations.yml 的 actions），
     * 而不是原版「点一下立刻结算」。开了这个，动作播放窗口内玩家的原版近战输出
     * 会被取消，命中判定交给轨道上的 mm-skill / command
     */
    val suppressAttackMs: Long = section.getLong("suppress-vanilla-attack", 0L).coerceAtLeast(0L)

    /**
     * 动作期间免疫伤害的毫秒数（无敌帧）；0 = 不免疫。
     *
     * 与 suppress-vanilla-attack 方向相反：那个压制玩家**打出去**的伤害，
     * 这个免疫玩家**挨到**的伤害。翻滚闪避靠它才名副其实。
     * 虚空与 /kill 不在免疫范围，避免卡在世界外无法死亡
     */
    val invulnerableMs: Long = section.getLong("invulnerable", 0L).coerceAtLeast(0L)

    /** 独占全身：播放期间压制出招层，避免翻滚与挥砍各演各的 */
    val exclusive: Boolean = section.getBoolean("exclusive", false)

    /** 是否允许空中触发（跳跃中翻滚） */
    val airborne: Boolean = section.getBoolean("airborne", true)

    /** 是否必须站在地面才能触发 */
    val requireGround: Boolean = section.getBoolean("require-ground", false)

    /** 水平位移距离（格）；0 = 只播动画不位移 */
    val distance: Double = section.getDouble("distance", 0.0).coerceAtLeast(0.0)

    /** 位移持续毫秒；0 = 跟随动画时长（由客户端按剪辑长度取） */
    val dashDuration: Long = section.getLong("dash-duration", 0L).coerceAtLeast(0L)

    /** 触发瞬间的垂直冲量（格/tick），跳跃翻滚可给正值；0 = 不改变竖直速度 */
    val vertical: Double = section.getDouble("vertical", 0.0)

    /** 方向 -> 动画名。键：forward / backward / left / right / 四个斜向 / none */
    val directions: Map<String, String> = section.getConfigurationSection("directions")
        ?.let { dir ->
            dir.getKeys(false).mapNotNull { name ->
                dir.getString(name)?.takeIf { it.isNotBlank() }?.let { name.lowercase() to it }
            }.toMap()
        } ?: emptyMap()

    /**
     * 服务端把关开关。
     *
     * false（缺省）= 客户端按键即播，零往返延迟，服务端只下发定义；
     * true = 客户端不本地播放，改上报请求，服务端抛
     * LanternPlayerActionRequestEvent 供附属（能量/耐力/冷却）决定放不放，
     * 没有附属时服务端照常播出。代价是多一个 RTT 的出招延迟
     */
    val serverChecked: Boolean = section.getBoolean("server-checked", false)

    /**
     * 触发源：key（按键，缺省）或 attack（原版挥击边沿，用于连招）。
     * 连招走挥击边沿而不是自定按键，是为了继承原版的攻击冷却与命中判定
     */
    val trigger: String = section.getString("trigger", "key")!!

    /**
     * 限定生效的外观 id（costumes.yml 的条目名）。
     * 留空 = 不限定，作为所有外观的回落连招。
     * 动画组切换后招式要跟着换，靠这个字段绑定
     */
    val costume: String = section.getString("costume", "")!!

    /**
     * 连招段。每段可配：
     *   animation   动画名
     *   cancel-at   本段播到该进度（0-1）即可被下一段接替——连招不顿挫的来源：
     *               不必等整段播完
     *   window      距上次出手多久内的输入还算同一套连招（毫秒），超时回第一段
     *   transition  / exit-transition  该段的起手与收尾过渡 tick
     */
    val steps: List<Map<String, Any>> = section.getMapList("steps")
        .mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            val map = raw as? Map<String, Any> ?: return@mapNotNull null
            if (map["animation"]?.toString().isNullOrBlank()) null else map
        }

    /** 定义是否完整可用：方向动作要有 directions，连招要有 steps */
    val valid: Boolean
        get() = file.isNotBlank() &&
            (steps.isNotEmpty() || (key.isNotBlank() && directions.isNotEmpty()))
}
