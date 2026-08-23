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
    val animationState: EntityAnimationState
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
            return CostumeRenderContext(playerId, pose, animationState)
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
}
