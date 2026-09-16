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
        player.drive(
            clips,
            AnimationControlStore.get(uuid),
            entity,
            animationStates,
            spawned.add(uuid)
        )
        return player.pose
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

    @JvmStatic
    fun reset() {
        players.clear()
    }

    /** 实体离开世界时释放其播放器状态（移动检测、活跃剪辑等）；
     *  spawn 集合同步清除，重连/换维度回来的实体重新触发出生动画 */
    @JvmStatic
    fun remove(uuid: UUID) {
        players.remove(uuid)
        spawned.remove(uuid)
    }

    /** 诊断（P1Diag）：输出该实体的状态表与各层实时剪辑 */
    @JvmStatic
    fun describe(uuid: UUID): String {
        val player = players[uuid] ?: return "no-player"
        return player.describeDiagnostic()
    }
}
