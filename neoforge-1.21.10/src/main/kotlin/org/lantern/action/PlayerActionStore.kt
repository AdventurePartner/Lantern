package org.lantern.action

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import org.lantern.animation.AnimationHost
import org.lantern.animation.AnimationRepository
import org.lantern.core.action.ActionDef
import org.lantern.core.action.PlayerActionDefs

/**
 * 玩家主动动作的运行时半边：触发判定、位移施加与事件上报。
 * 定义解析在 common-core 的 [PlayerActionDefs]（packet 19 直达），这里只做
 * 读本地玩家输入/物理的部分。
 */
object PlayerActionStore {

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

    /** 每个动作各自的冷却到期时刻 */
    private val cooldowns = ConcurrentHashMap<String, Long>()

    /** 动作定义与冷却一起作废（退服） */
    fun clear() {
        PlayerActionDefs.clear()
        cooldowns.clear()
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
        if (org.lantern.core.input.InputLockStore.isLocked("move")) return false

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

        val clip = AnimationRepository.clips(def.file)?.get(PlayerActionDefs.resolveAnimation(def, direction)) ?: return false
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
        if (player.isDeadOrDying || player.isPassenger || org.lantern.core.input.InputLockStore.isLocked("move")) {
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
