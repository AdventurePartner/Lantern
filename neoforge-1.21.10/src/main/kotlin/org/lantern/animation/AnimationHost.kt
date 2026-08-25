package org.lantern.animation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.lantern.model.enums.EntityAnimationState
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

    @JvmStatic
    fun drive(
        entity: Entity,
        wrapper: CustomModelWrapper,
        processor: AnimationProcessor<*>
    ) {
        val clips = AnimationRepository.clips(wrapper.animationLocation)
        if (clips.isNullOrEmpty()) return
        val uuid = entity.uuid
        val player = players.computeIfAbsent(uuid) { AnimationPlayer(it) }
        val actionState = if (entity is LivingEntity && entity.isDeadOrDying) {
            EntityAnimationState.DEATH
        } else {
            null
        }
        player.drive(
            clips,
            AnimationControlStore.get(uuid),
            actionState,
            entity.x,
            entity.y,
            entity.z,
            wrapper.animationStates
        )
        player.applyTo(processor)
    }

    @JvmStatic
    fun reset() {
        players.clear()
    }
}
