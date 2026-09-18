package org.lantern.action

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.animation.AnimationHost
import org.lantern.animation.AnimationRepository

/**
 * 玩家主动动作库（按键触发，客户端本地播放）。
 *
 * 为什么本地触发而不是走服务端播控：
 * 1. 零往返延迟——翻滚这类闪避动作对输入响应极敏感，一个来回 100ms 就废了；
 * 2. 不占广播——播控是全服广播，MMO 规模下每次翻滚都乘以在线人数；
 * 3. 方向判定要用玩家的**按键输入**（前/后/左/右的组合），这个量只有本地客户端有，
 *    服务端拿到的只有移动结果，斜向翻滚分不出来。
 *
 * 服务端只负责下发定义（packet 19），动画资产经资源包通道到达客户端。
 */
object PlayerActionStore {

    /**
     * 一条动作定义。
     *
     * @param keySpec 触发键，与 keys.yml 同一套写法（如 "left_alt"、"ctrl+space"）
     * @param file 动画库文件，独立于 costume 绑定的动画文件
     * @param directions 方向 -> 动画名；八方向 + none（无方向输入时的原地段）
     * @param toCombatLayer true=上身出招层（与移动并存），false=全身运动层
     * @param uninterruptible 播放期间拒绝被同层新动作顶替（无敌帧动作）
     */
    class ActionDef(
        val id: String,
        val keySpec: String,
        val file: ResourceLocation,
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
     * 进行中的位移。翻滚的位移由客户端本地施加——移动在 Minecraft 里是客户端权威的
     * （客户端算完位置再上报），所以本地驱动既与动画严格同步，又能走原版的碰撞处理，
     * 撞墙自然停下。服务端往返施加速度会让位移慢半拍，动作类手感立刻就废了。
     */
    private class ActiveDash(
        val dirX: Double,
        val dirZ: Double,
        val distance: Double,
        val durationMs: Long,
        val startMs: Long
    )

    @Volatile
    private var activeDash: ActiveDash? = null

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
        val file: ResourceLocation,
        val toCombatLayer: Boolean,
        val uninterruptible: Boolean,
        val steps: List<ComboStep>
    )

    @Volatile
    private var attackCombos: List<ComboDef> = emptyList()

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

    @Volatile
    private var definitions: List<ActionDef> = emptyList()

    /** 每个动作各自的冷却到期时刻 */
    private val cooldowns = ConcurrentHashMap<String, Long>()

    fun definitions(): List<ActionDef> = definitions

    fun clear() {
        definitions = emptyList()
        attackCombos = emptyList()
        cooldowns.clear()
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
            val filePath = obj.get("file")?.asString?.takeIf { it.isNotBlank() } ?: continue
            val file = runCatching {
                if (filePath.contains(':')) ResourceLocation.parse(filePath)
                else ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, filePath)
            }.getOrNull() ?: continue

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
        cooldowns.clear()
        Lantern.logger.info(
            "[Lantern] Loaded {} player action(s), {} combo set(s)",
            parsed.size, attackCombos.size
        )
    }

    /**
     * 按键按下时触发。方向取自本地玩家的移动按键组合——八方向靠 forward/backward
     * 与 left/right 的叠加区分，比用速度矢量更准（斜向移动的速度分量会被墙面、
     * 冰面、被动位移污染，按键位不会）。
     *
     * @return 是否真的播出（冷却中、条件不满足、资产缺失都返回 false）
     */
    fun trigger(def: ActionDef): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        // 死亡、旁观、骑乘状态下没有"出招"这回事；尸体被推 4 格、旁观者翻滚都是漏洞
        if (player.isDeadOrDying || player.isSpectator || player.isPassenger) return false
        // 输入锁压住移动时不接方向动作：锁是「这段时间不许动」，翻滚是最大幅度的动。
        // 这里读到的方向键已被锁清空，放行的话会朝正前冲 4 格再拿 500ms 无敌
        if (org.lantern.input.InputLockStore.isLocked("move")) return false

        val now = System.currentTimeMillis()
        cooldowns[def.id]?.let { if (now < it) return false }

        val onGround = player.onGround()
        if (def.requireOnGround && !onGround) return false
        if (!def.allowAirborne && !onGround) return false

        val direction = currentDirection()

        // 服务端把关的条目：不本地播放，把请求发上去，由服务端决定播不播。
        // 复用现有的 C2S 动画事件通道（animation 字段填动作 id），零协议变更。
        // 冷却仍在本地记一份——否则连点会把请求包刷成洪水，服务端那边拦得再干净
        // 带宽也已经花掉了
        if (def.serverChecked) {
            val sender = org.lantern.internal.network.NetworkParser.animationEventSender ?: return false
            sender(player.uuid, def.id, "request:$direction")
            if (def.cooldownMs > 0) cooldowns[def.id] = now + def.cooldownMs
            return true
        }

        val clip = AnimationRepository.clips(def.file)?.get(resolveAnimation(def, direction)) ?: return false
        val played = AnimationHost.triggerAction(
            player.uuid,
            clip,
            def.transitionTicks * 0.05f,
            def.exitTicks * 0.05f,
            def.speed,
            def.uninterruptible,
            def.exclusive,
            def.toCombatLayer
        )
        if (!played) return false

        if (def.cooldownMs > 0) cooldowns[def.id] = now + def.cooldownMs
        if (def.suppressAttackMs > 0) {
            // 上报压制窗口：伤害判定在服务端，客户端只能告诉它「现在开始别结算原版攻击」
            org.lantern.internal.network.NetworkParser.animationEventSender
                ?.invoke(player.uuid, def.id, "suppress:${def.suppressAttackMs}")
        }
        if (def.invulnerableMs > 0) {
            // 无敌帧同理：起播瞬间上报，服务端在窗口内取消该玩家受到的伤害
            org.lantern.internal.network.NetworkParser.animationEventSender
                ?.invoke(player.uuid, def.id, "invuln:${def.invulnerableMs}")
        }
        startDash(def, direction, clip.length)
        return true
    }

    /** 取连招某段的剪辑（连招动画库独立于 costume 的动画文件） */
    fun comboClip(combo: ComboDef, step: ComboStep): org.lantern.animation.ClipData? =
        AnimationRepository.clips(combo.file)?.get(step.animation)

    /** 起动位移：方向取自八方向键名，时长缺省跟随剪辑长度，与动画同步收尾 */
    private fun startDash(def: ActionDef, direction: String, clipLengthSeconds: Float) {
        val player = Minecraft.getInstance().player ?: return
        if (def.vertical != 0.0) {
            val m = player.deltaMovement
            player.deltaMovement = m.add(0.0, def.vertical, 0.0)
        }
        if (def.distance <= 0.0) {
            activeDash = null
            return
        }
        val durationMs = if (def.dashDurationMs > 0) def.dashDurationMs
        else (clipLengthSeconds * 1000f).toLong().coerceAtLeast(1L)

        // 朝向基准取玩家视角 yaw：MC 的 yaw 0 = +Z（南），前向 = (-sin, cos)，
        // 右向 = (-cos, -sin)（面朝南时右手指向西 -X）
        val yaw = Math.toRadians(player.yRot.toDouble())
        val fx = -Math.sin(yaw)
        val fz = Math.cos(yaw)
        val rx = -Math.cos(yaw)
        val rz = -Math.sin(yaw)

        var dx = 0.0
        var dz = 0.0
        when (direction) {
            "forward" -> { dx = fx; dz = fz }
            "backward" -> { dx = -fx; dz = -fz }
            "left" -> { dx = -rx; dz = -rz }
            "right" -> { dx = rx; dz = rz }
            "forward_left" -> { dx = fx - rx; dz = fz - rz }
            "forward_right" -> { dx = fx + rx; dz = fz + rz }
            "backward_left" -> { dx = -fx - rx; dz = -fz - rz }
            "backward_right" -> { dx = -fx + rx; dz = -fz + rz }
            else -> { dx = fx; dz = fz }   // 无方向输入时朝正前
        }
        val len = Math.sqrt(dx * dx + dz * dz)
        if (len < 1.0E-6) return
        activeDash = ActiveDash(dx / len, dz / len, def.distance, durationMs, System.currentTimeMillis())
    }

    /**
     * 每 tick 驱动位移（由客户端 tick 回调调用）。
     *
     * 速度按线性缓出 v(t) = v0·(1−t)，积分得总位移 = v0·T/2，故 v0 = 2·distance/T——
     * 配置里写几格就走几格，不受地面摩擦影响（摩擦只作用在残余速度上，
     * 而这里每 tick 直接改写水平分量）。竖直分量保留，空中翻滚不会被拽平。
     */
    fun tickDash() {
        val dash = activeDash ?: return
        val player = Minecraft.getInstance().player ?: run { activeDash = null; return }
        // 位移的生命周期跟着动作走：动作被播控顶掉由 AnimationPlayer 清；这里兜住
        // 起手后才到达的输入锁与死亡/骑乘——人不该在锁住或死了之后还在滑
        if (player.isDeadOrDying || player.isPassenger || org.lantern.input.InputLockStore.isLocked("move")) {
            activeDash = null
            return
        }
        val progress = (System.currentTimeMillis() - dash.startMs).toDouble() / dash.durationMs
        if (progress >= 1.0) {
            activeDash = null
            return
        }
        val perSecond = 2.0 * dash.distance / (dash.durationMs / 1000.0)
        val perTick = perSecond * (1.0 - progress) * 0.05
        val m = player.deltaMovement
        player.deltaMovement = net.minecraft.world.phys.Vec3(dash.dirX * perTick, m.y, dash.dirZ * perTick)
    }

    /** 退服/切世界时清掉进行中的位移 */
    fun clearDash() {
        activeDash = null
    }

    private fun resolveAnimation(def: ActionDef, direction: String): String {
        // 配了斜向就用斜向，没配回落到相邻主方向，再回落 none——
        // 资产只有四段前后左右时，斜向自动走最接近的那段
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

    /** 当前移动按键组合 -> 八方向键名 */
    private fun currentDirection(): String {
        val keys = Minecraft.getInstance().player?.input?.keyPresses ?: return "none"
        val f = keys.forward()
        val b = keys.backward()
        val l = keys.left()
        val r = keys.right()
        return when {
            f && l && !b && !r -> "forward_left"
            f && r && !b && !l -> "forward_right"
            b && l && !f && !r -> "backward_left"
            b && r && !f && !l -> "backward_right"
            f && !b -> "forward"
            b && !f -> "backward"
            l && !r -> "left"
            r && !l -> "right"
            else -> "none"
        }
    }
}
