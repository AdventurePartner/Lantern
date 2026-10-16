package org.lantern.animation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.core.anim.AnimationPlayer
import org.lantern.core.anim.clip.ClipData
import org.lantern.core.anim.control.AnimationControlStore
import org.lantern.core.anim.statemap.AnimationStateMapping
import org.lantern.model.wrapper.CustomModelWrapper
import org.lantern.platform.TimeBridge
import software.bernie.geckolib.animation.AnimationProcessor

/**
 * Lantern 自托管动画宿主入口（Fabric 1.21.1 / Forge 1.21.1 / Forge 1.20.1；1.21.10 有同名副本）。
 *
 * 取代 GeckoLib 的谓词控制器体系：GeckoLib 只保留资产解析与渲染，动画决策、时间轴、
 * 混合、骨骼写入全部由 common-core 的 AnimationPlayer 完成，且是唯一骨骼写入方。
 * 1.21.1 / 1.20.1 没有“提取 / 提交”两段式，渲染器在 render 里先算姿势再立即写骨骼
 */
object AnimationHost {

    private val players = ConcurrentHashMap<UUID, AnimationPlayer>()

    /**
     * 每个实体上一次被驱动的帧标识。同一玩家可能同时佩戴多个外观槽，一帧内被驱动多次——
     * 边沿（起跳/落地/挥击）会被重复消费，dt 也被瓜分。按帧判重：本帧首次才真正推进
     */
    private val lastDrivenFrame = ConcurrentHashMap<UUID, Long>()

    // 已触发过 spawn 的实体：跨 reset() 存活，只有实体离场（remove）才清除——
    // /lantern reload 会清空 players 重建，若以 player 生命周期判定 spawn，
    // 全部在线实体会在重载后同时重播出生动画
    private val spawned = ConcurrentHashMap.newKeySet<UUID>()

    @JvmStatic
    fun drivePose(entity: Entity, wrapper: CustomModelWrapper): Map<String, FloatArray>? =
        drivePose(entity, wrapper.animationLocation, wrapper.animationStates)

    /** 外观模型与实体模型共用同一播放器内核：hostDriven 外观以此入口驱动 */
    @JvmStatic
    fun drivePose(
        entity: Entity,
        animationLocation: ResourceLocation,
        animationStates: AnimationStateMapping
    ): Map<String, FloatArray>? {
        val clips = AnimationRepository.clips(animationLocation.toString())
        if (clips.isNullOrEmpty()) {
            // 动画资产缺失：播控既无法播放也无法到期，清掉残留条目；
            // 返回空姿势让骨骼回退静态初始值，而不是冻结在上一帧
            AnimationControlStore.stop(entity.uuid, null)
            return emptyMap()
        }
        val uuid = entity.uuid
        val player = players.computeIfAbsent(uuid) { newPlayer(it) }
        // 帧标识 = 游戏刻 × 1000 + 帧内插值的千分位，同一渲染帧内恒定
        val frameId = entity.level().gameTime * 1000L + (TimeBridge.partialTick() * 1000f).toLong()
        if (lastDrivenFrame.put(uuid, frameId) != frameId) {
            player.drive(
                clips,
                AnimationControlStore.get(uuid),
                ActorSnapshots.capture(entity),
                animationStates,
                spawned.add(uuid)
            )
        }
        return player.pose
    }

    /**
     * 播放器内核的平台装配：库查询走资源仓库、连招查询组合外观 id、
     * 事件上报桥到 C2S 通道、位移取消只对本地玩家生效
     */
    private fun newPlayer(uuid: UUID): AnimationPlayer = AnimationPlayer(
        uuid,
        clipLibrary = AnimationRepository::clips,
        comboLookup = { id ->
            org.lantern.core.action.PlayerActionDefs.attackCombo(
                org.lantern.costume.handler.CostumeHandler.hostCostumeId(id)
            )
        },
        eventSink = { id, animation, event ->
            org.lantern.internal.network.NetworkParser.animationEventSender?.invoke(id, animation, event)
        },
        dashCancel = { id ->
            if (Minecraft.getInstance().player?.uuid == id) {
                org.lantern.action.PlayerActionStore.clearDash()
            }
        }
    )

    /**
     * 本地玩家在第一人称下不渲染自己，播放器得不到渲染回调：时间轴不走、播控不到期、
     * 动作槽不释放、finish 永不上报。每客户端 tick 补驱动一次——本 tick 已经被渲染
     * 驱动过就跳过，不会重复消费边沿
     */
    @JvmStatic
    fun tickLocalPlayer() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val uuid = player.uuid
        if (!players.containsKey(uuid)) return
        val last = lastDrivenFrame[uuid] ?: -1L
        if (last >= level.gameTime * 1000L) return
        val wrapper = org.lantern.costume.handler.CostumeHandler.hostWrapper(uuid) ?: return
        drivePose(player, wrapper.animationLocation, wrapper.animationStates)
    }

    @JvmStatic
    fun applyPose(
        processor: AnimationProcessor<*>,
        pose: Map<String, FloatArray>?,
        initial: Map<String, FloatArray>
    ) {
        applyPoseToBones(processor, pose, initial)
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

    /** 实体离开世界时释放其播放器状态；spawn 集合同步清除，换维度回来的实体重新触发出生动画 */
    @JvmStatic
    fun remove(uuid: UUID) {
        players.remove(uuid)
        spawned.remove(uuid)
        lastDrivenFrame.remove(uuid)
    }
}
