package org.lantern.costume.bone

import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelPart

class MutableBoneRotation(var rotX: Float = 0f, var rotY: Float = 0f, var rotZ: Float = 0f) {
    fun set(x: Float, y: Float, z: Float) { rotX = x; rotY = y; rotZ = z }
}

class PlayerBoneSnapshot(
    val head: MutableBoneRotation = MutableBoneRotation(),
    val body: MutableBoneRotation = MutableBoneRotation(),
    val leftArm: MutableBoneRotation = MutableBoneRotation(),
    val rightArm: MutableBoneRotation = MutableBoneRotation(),
    val leftLeg: MutableBoneRotation = MutableBoneRotation(),
    val rightLeg: MutableBoneRotation = MutableBoneRotation()
) {
    companion object {
        @JvmStatic
        fun capture(model: PlayerModel<*>, into: PlayerBoneSnapshot = PlayerBoneSnapshot()): PlayerBoneSnapshot {
            capturePart(model.head, into.head)
            capturePart(model.body, into.body)
            capturePart(model.leftArm, into.leftArm)
            capturePart(model.rightArm, into.rightArm)
            capturePart(model.leftLeg, into.leftLeg)
            capturePart(model.rightLeg, into.rightLeg)
            return into
        }

        private fun capturePart(part: ModelPart, into: MutableBoneRotation) {
            val initialPose = part.initialPose
            into.set(
                -(part.xRot - initialPose.xRot),
                -(part.yRot - initialPose.yRot),
                part.zRot - initialPose.zRot
            )
        }
    }
}
