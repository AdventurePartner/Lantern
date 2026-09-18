package org.lantern.animation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.entity.Entity
import org.lantern.model.renderstate.AnimationControlStore
import org.lantern.model.wrapper.CustomModelWrapper
import software.bernie.geckolib.animatable.processing.AnimationProcessor

/**
 * Lantern 自托管动画宿主入口。
 *
 * 由渲染器在每帧实体渲染状态提取阶段调用（GenericGeoRenderer.addRenderData），
 * 取代 GeckoLib 的谓词控制器体系：GeckoLib 仅保留资产解析与渲染，
 * 动画决策、时间轴、混合、骨骼写入全部由本包完成，且是唯一骨骼写入方。
 */
object AnimationHost {

    private val players = ConcurrentHashMap<UUID, AnimationPlayer>()

    /**
     * 每个实体上一次被驱动的帧标识。
     *
     * 同一个玩家可能同时佩戴多个外观槽，渲染时每个外观各调一次渲染器，
     * 于是同一个播放器在一帧内被驱动多次——边沿（起跳/落地/挥击）会被重复消费，
     * dt 也被瓜分成几份。这里按帧判重：本帧首次才真正推进，后续调用直接复用姿势
     */
    private val lastDrivenFrame = ConcurrentHashMap<UUID, Long>()

    // 已触发过 spawn 的实体集合：跨 reset() 存活，只有实体离场（remove）才清除——
    // /lantern reload 会清空 players 重建，若以 player 生命周期判定 spawn，
    // 全部在线实体会在重载后同时重播出生动画；spawn 语义是实体生命周期内首次渲染
    private val spawned = ConcurrentHashMap.newKeySet<UUID>()

    /** 提取阶段调用：只计算姿势（存入 player.pose），不写骨骼 */
    @JvmStatic
    fun drivePose(entity: Entity, wrapper: CustomModelWrapper): Map<String, FloatArray>? {
        return drivePose(entity, wrapper.animationLocation, wrapper.animationStates)
    }

    /**
     * P1 玩家宿主化：外观模型与实体模型共用同一播放器内核。
     * Costume 渲染器（hostDriven 外观）以此入口驱动，参数取自 CostumeModelWrapper
     * 的同名字段——动画库定位与状态表，玩家与替换实体走完全相同的层栈/播控/姿态链
     */
    @JvmStatic
    fun drivePose(
        entity: Entity,
        animationLocation: net.minecraft.resources.ResourceLocation,
        animationStates: org.lantern.model.wrapper.AnimationStateMapping
    ): Map<String, FloatArray>? {
        val clips = AnimationRepository.clips(animationLocation)
        if (clips.isNullOrEmpty()) {
            // 动画资产缺失：播控既无法播放也无法到期，清掉残留条目；
            // 返回空姿势让骨骼回退静态初始值，而不是冻结在上一帧
            AnimationControlStore.stop(entity.uuid, null)
            return emptyMap()
        }
        val uuid = entity.uuid
        val player = players.computeIfAbsent(uuid) { AnimationPlayer(it) }
        // 帧标识 = 游戏刻 × 1000 + 帧内插值的千分位，同一渲染帧内恒定
        val level = entity.level()
        val frameId = level.gameTime * 1000L +
            (net.minecraft.client.Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false) * 1000f).toLong()
        val firstThisFrame = lastDrivenFrame.put(uuid, frameId) != frameId
        if (firstThisFrame) {
            player.drive(
                clips,
                AnimationControlStore.get(uuid),
                entity,
                animationStates,
                spawned.add(uuid)
            )
        }
        return player.pose
    }

    /**
     * 本地玩家在第一人称下不渲染自己，播放器得不到渲染回调：时间轴不走、播控不到期、
     * 动作槽不释放（翻滚只能成功一次）、finish 永不上报。每客户端 tick 补驱动一次——
     * 本 tick 已经被渲染驱动过就跳过，不会重复消费边沿
     */
    @JvmStatic
    fun tickLocalPlayer() {
        val client = net.minecraft.client.Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val uuid = player.uuid
        if (!players.containsKey(uuid)) return
        val tickId = level.gameTime * 1000L
        val last = lastDrivenFrame[uuid] ?: -1L
        // 渲染帧标识 = 刻 × 1000 + 千分位插值；本刻内任何一帧驱动过即视为已驱动
        if (last >= tickId) return
        val wrapper = org.lantern.costume.handler.CostumeHandler.hostWrapper(uuid) ?: return
        drivePose(player, wrapper.animationLocation, wrapper.animationStates)
    }

    /** 渲染阶段调用（submit 内，逐实体串行）：从 DataTicket 读回姿势写入骨骼 */
    @JvmStatic
    fun applyPose(
        processor: AnimationProcessor<*>,
        pose: Map<String, FloatArray>?,
        initial: Map<String, FloatArray>
    ) {
        org.lantern.animation.applyPoseToBones(processor, pose, initial)
    }

    /**
     * 外部注入一次性动作（按键触发的翻滚等）。
     * 播放器只在该实体渲染过之后才存在——未渲染时静默失败，调用方据此回落
     */
    @JvmStatic
    fun triggerAction(
        uuid: UUID,
        clip: ClipData,
        transitionSeconds: Float,
        exitSeconds: Float,
        speed: Float,
        uninterruptible: Boolean,
        exclusive: Boolean,
        toCombatLayer: Boolean
    ): Boolean = players[uuid]?.triggerAction(
        clip, transitionSeconds, exitSeconds, speed, uninterruptible, exclusive, toCombatLayer
    ) ?: false

    @JvmStatic
    fun reset() {
        players.clear()
        lastDrivenFrame.clear()
    }

    /** 实体离开世界时释放其播放器状态（移动检测、活跃剪辑等）；
     *  spawn 集合同步清除，重连/换维度回来的实体重新触发出生动画 */
    @JvmStatic
    fun remove(uuid: UUID) {
        players.remove(uuid)
        spawned.remove(uuid)
        lastDrivenFrame.remove(uuid)
    }

    /** 诊断（P1Diag）：输出该实体的状态表与各层实时剪辑 */
    @JvmStatic
    fun describe(uuid: UUID): String {
        val player = players[uuid] ?: return "no-player"
        return player.describeDiagnostic()
    }
}
