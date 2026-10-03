package org.lantern.costume.renderstate

import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.context.ContextKey
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.model.enums.EntityAnimationState
import software.bernie.geckolib.constant.dataticket.DataTicket
import java.util.UUID

data class CostumeRenderContext(
    val playerId: UUID,
    val pose: PlayerBoneSnapshot,
    val animationState: EntityAnimationState,
    /**
     * P1 玩家宿主化：hostDriven 外观驱动所需的玩家实体（姿态链判定/位移实测
     * 需要实体状态；AvatarRenderState 不携带实体引用，从客户端实体表按 UUID 查）。
     * 实体不在渲染距离时为 null，该帧 hostDriven 外观跳过驱动
     */
    val entity: net.minecraft.world.entity.Entity?
) {
    companion object {
        @JvmStatic
        fun from(playerId: UUID, pose: PlayerBoneSnapshot, state: AvatarRenderState): CostumeRenderContext {
            val animationState = when {
                state.deathTime > 0 -> EntityAnimationState.DEATH
                state.hasRedOverlay -> EntityAnimationState.HURT
                state.attackTime > 0 -> EntityAnimationState.ATTACK
                state.walkAnimationSpeed > 0.01f -> EntityAnimationState.WALK
                else -> EntityAnimationState.IDLE
            }
            val entity = net.minecraft.client.Minecraft.getInstance().level?.getPlayerByUUID(playerId)
            return CostumeRenderContext(playerId, pose, animationState, entity)
        }
    }
}

object CostumeRenderData {
    @JvmField
    val PLAYER_UUID = ContextKey<UUID>(ResourceLocation.fromNamespaceAndPath("lantern", "player_uuid"))

    @JvmField
    val CAMERA_STATE = ContextKey<CameraRenderState>(
        ResourceLocation.fromNamespaceAndPath("lantern", "camera_state")
    )

    @JvmField
    val PLAYER_POSE: DataTicket<PlayerBoneSnapshot> = DataTicket.create(
        "lantern:player_pose",
        PlayerBoneSnapshot::class.java
    )

    @JvmField
    val ANIMATION_STATE: DataTicket<EntityAnimationState> = DataTicket.create(
        "lantern:costume_animation_state",
        EntityAnimationState::class.java
    )

    /** hostDriven 外观的 AnimationHost 姿势（P1 玩家宿主化） */
    @JvmField
    @Suppress("UNCHECKED_CAST")
    val HOST_POSE: DataTicket<Map<String, FloatArray>> = DataTicket.create(
        "lantern:host_pose",
        Map::class.java
    ) as DataTicket<Map<String, FloatArray>>

    /** hostDriven 本帧渲染上下文（postRender 手持物品渲染取玩家实体用） */
    @JvmField
    val HOST_CONTEXT: DataTicket<CostumeRenderContext> = DataTicket.create(
        "lantern:host_context",
        CostumeRenderContext::class.java
    )
}
