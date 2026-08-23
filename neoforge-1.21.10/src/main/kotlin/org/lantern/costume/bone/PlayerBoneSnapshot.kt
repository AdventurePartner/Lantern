package org.lantern.costume.bone

import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelPart

data class BoneRotation(val x: Float, val y: Float, val z: Float)

data class PlayerBoneSnapshot(
    val head: BoneRotation,
    val body: BoneRotation,
    val leftArm: BoneRotation,
    val rightArm: BoneRotation,
    val leftLeg: BoneRotation,
    val rightLeg: BoneRotation
) {
    companion object {
        @JvmStatic
        fun capture(model: PlayerModel): PlayerBoneSnapshot = PlayerBoneSnapshot(
            capture(model.head),
            capture(model.body),
            capture(model.leftArm),
            capture(model.rightArm),
            capture(model.leftLeg),
            capture(model.rightLeg)
        )

        private fun capture(part: ModelPart): BoneRotation {
            val initial = part.initialPose
            return BoneRotation(
                -(part.xRot - initial.xRot),
                -(part.yRot - initial.yRot),
                part.zRot - initial.zRot
            )
        }
    }
}
