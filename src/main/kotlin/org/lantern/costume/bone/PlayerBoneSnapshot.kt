package org.lantern.costume.bone

import net.minecraft.client.model.PlayerModel

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
            into.head.set(model.head.xRot, model.head.yRot, model.head.zRot)
            into.body.set(model.body.xRot, model.body.yRot, model.body.zRot)
            into.leftArm.set(model.leftArm.xRot, model.leftArm.yRot, model.leftArm.zRot)
            into.rightArm.set(model.rightArm.xRot, model.rightArm.yRot, model.rightArm.zRot)
            into.leftLeg.set(model.leftLeg.xRot, model.leftLeg.yRot, model.leftLeg.zRot)
            into.rightLeg.set(model.rightLeg.xRot, model.rightLeg.yRot, model.rightLeg.zRot)
            return into
        }
    }
}
