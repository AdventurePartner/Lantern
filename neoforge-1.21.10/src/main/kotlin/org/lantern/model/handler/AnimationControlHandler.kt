package org.lantern.model.handler

import java.util.UUID
import kotlin.math.abs
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.animation.AnimationRepository
import org.lantern.model.renderstate.AnimationControlStore

/**
 * packetId 15 动画播控入口：校验目标实体确由 Lantern 实体模型渲染后，写入强制动画存储。
 * 在共享 NetworkParser 的钩子上注册，仅 neoforge 1.21.10 实现了完整链路。
 *
 * NeoForge 的客户端载荷处理器默认就在主线程（RegisterClientPayloadHandlersEvent.register
 * 走 HandlerThread.MAIN），所以这里**同步**解析，不再 Minecraft.execute 推后一拍：
 * 外观分配包是同步落表的，播控晚一拍就会落到切换后的动画库上——
 * 「播控生效: 库=player_default」那条日志就是这么来的。
 *
 * 实体还没到（服务端在 summon 同一 tick 就发了播控）或自定义名还没同步时，
 * 进重试队列按 tick 重试，几个 tick 内落地，超时才放弃并报告。
 */
object AnimationControlHandler {

    /**
     * 播控落点去重集：每个「动画名 + 库 + 结果」只打一条。
     *
     * 播控是整条链上最容易静默失败的一段——指令发出去了、客户端收到了，但目标
     * 解析不出动画库就直接丢弃，画面上只表现为「技能没特效」。键里带上库：
     * 同名动画落到不同库是不同的事，尤其是外观切换前后
     */
    private val reported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun resetDiagnostics() {
        reported.clear()
    }

    private fun report(key: String, warn: Boolean, message: String, vararg args: Any?) {
        if (!reported.add(key)) return
        if (warn) Lantern.logger.warn(message, *args) else Lantern.logger.info(message, *args)
    }

    /** 时间戳到期的兜底余量：正常路径由播放器时间轴到头即刻释放（见 AnimationPlayer），
     *  过渡由淡化机制完成，此处不再叠加过渡 tick */
    private const val EXPIRY_MARGIN_MS = 100L

    /** 实体/名字尚未同步时的重试上限（tick）。summon 与播控同 tick 发出时差通常在 1~2 tick */
    private const val RETRY_TICKS = 10

    private class PendingPlay(
        val uuid: UUID,
        val animation: String,
        val transition: Int,
        val loop: Boolean,
        val speed: Float,
        val uninterruptible: Boolean,
        val toCombatLayer: Boolean,
        val library: ResourceLocation?,
        val seq: Long,
        val exitTicks: Int,
        var ticksLeft: Int
    )

    /** 等实体落地的播控；主线程独占访问（收包与 tick 都在主线程） */
    private val pending = ArrayList<PendingPlay>()

    fun handle(
        uuid: UUID,
        action: String,
        animation: String,
        transition: Int,
        loop: Boolean,
        speed: Float,
        seekSeconds: Float,
        uninterruptible: Boolean = false,
        toCombatLayer: Boolean = false,
        library: ResourceLocation? = null,
        seq: Long = -1L,
        exitTicks: Int = -1
    ) {
        when (action) {
            "stop" -> {
                // 对同一动画的待重试项一并撤销：还没落地就被 stop 的播控不该在几 tick 后复活
                pending.removeIf { it.uuid == uuid && it.animation == animation }
                AnimationControlStore.stop(uuid, animation)
            }
            "pause", "resume", "seek" -> {
                // 动画名校验：指令指定的动画与当前播控不一致时忽略（防误操作打错实体）
                val entry = AnimationControlStore.get(uuid) ?: return
                if (entry.animation != animation) return
                when (action) {
                    "pause" -> AnimationControlStore.pause(uuid)
                    "resume" -> AnimationControlStore.resume(uuid)
                    else -> AnimationControlStore.seek(uuid, seekSeconds)
                }
            }
            "play" -> {
                val request = PendingPlay(
                    uuid, animation, transition, loop, speed, uninterruptible, toCombatLayer,
                    library, seq, exitTicks, RETRY_TICKS
                )
                if (!tryPlay(request)) {
                    // 同目标同动画的旧重试项被新指令覆盖
                    pending.removeIf { it.uuid == uuid && it.animation == animation }
                    pending.add(request)
                }
            }
        }
    }

    /** 每客户端 tick 驱动一次重试队列（NeoForgeClientEvents 调用） */
    fun tick() {
        if (pending.isEmpty()) return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val request = iterator.next()
            if (tryPlay(request)) {
                iterator.remove()
                continue
            }
            request.ticksLeft--
            if (request.ticksLeft <= 0) {
                iterator.remove()
                report("miss-entity:${request.animation}", true,
                    "[Lantern] 播控目标 {} 在 {} tick 内始终未就绪（实体不存在或无自定义名），动画 '{}' 被丢弃",
                    request.uuid, RETRY_TICKS, request.animation)
            }
        }
    }

    /**
     * 尝试落地一条播控。
     *
     * @return true = 已处理（成功写入，或确定无法处理已报告）；false = 目标暂未就绪，可重试
     */
    private fun tryPlay(request: PendingPlay): Boolean {
        val level = Minecraft.getInstance().level ?: return true
        val entity = level.getEntity(request.uuid) ?: return false
        val animation = request.animation

        // 目标动画库：指令自带的库优先（技能剪辑与外观解耦），否则玩家取 hostDriven 外观、
        // 实体走 entityModels 索引
        val animationLocation: ResourceLocation = request.library ?: run {
            if (entity is net.minecraft.world.entity.player.Player) {
                org.lantern.costume.handler.CostumeHandler.hostWrapper(request.uuid)?.animationLocation ?: run {
                    report("no-costume:$animation", true,
                        "[Lantern] 玩家 {} 没有 host-driven 外观，动画 '{}' 被丢弃", request.uuid, animation)
                    return true
                }
            } else {
                // 自定义名尚未同步：AddEntity 与随后的 SetEntityData 是两个包，中间可能隔一帧
                val name = entity.customName?.string ?: return false
                RendererHandler.getCustomModelWrapper(name)?.animationLocation ?: run {
                    report("no-model:$animation", true,
                        "[Lantern] 自定义名 '{}' 不在 entityModels 表里，动画 '{}' 被丢弃", name, animation)
                    return true
                }
            }
        }

        // 剪辑必须真的存在。查不到就放弃而不是按兜底时长挂着：挂着的十秒里运动层空置、
        // 本地动作全被封死，到期还会为一段没播过的动画上报 finish
        val clip = AnimationRepository.clips(animationLocation)?.get(animation) ?: run {
            report("no-clip:$animation@$animationLocation", true,
                "[Lantern] 动画 '{}' 不在动画库 {} 里，播控被丢弃（名字写错，或该外观的库里没有这段——" +
                    "技能动画请在 lanternanim 里用 file= 指定库）",
                animation, animationLocation)
            return true
        }

        // 负速度（倒放）合法：绝对值过小才视为无效回退 1.0
        val safeSpeed = if (abs(request.speed) > 0.01f) request.speed else 1.0f
        val expiresAtMs = if (request.loop) {
            0L
        } else {
            System.currentTimeMillis() + (clip.length * 1000f / abs(safeSpeed)).toLong() + EXPIRY_MARGIN_MS
        }
        // 退出过渡：指令给了就用，没给沿用起手过渡（技能起手多快、收尾就多快），下限一个 tick
        val exitSeconds = (if (request.exitTicks >= 0) request.exitTicks else request.transition)
            .coerceAtLeast(1) * 0.05f

        report("ok:$animation@$animationLocation", false,
            "[Lantern] 播控生效: 动画='{}' 目标={} 库={} mode={} 过渡={}t",
            animation, request.uuid, animationLocation, if (request.loop) "loop" else "once", request.transition)
        AnimationControlStore.play(
            request.uuid, animation, request.transition, request.loop, safeSpeed, expiresAtMs,
            request.uninterruptible, request.toCombatLayer, request.library, request.seq, exitSeconds
        )
        return true
    }

    /** 退服 / 重载：待重试项随会话作废 */
    fun clearPending() {
        pending.clear()
    }
}
