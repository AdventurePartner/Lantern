package org.lantern.model.geo

import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone

/** 共享 IsolatedBones 的 1.20.1 版：这一版 GeoBone 没有 updateRotation 这类批量 setter */
object IsolatedBones {

    class Result(
        val model: BakedGeoModel,
        val initialPose: Map<String, FloatArray>
    )

    fun clone(shared: BakedGeoModel): Result {
        val copies = shared.topLevelBones().map { cloneBone(it, null) }
        val initial = HashMap<String, FloatArray>()
        captureInitial(copies, initial)
        return Result(BakedGeoModel(copies, shared.properties()), initial)
    }

    private fun captureInitial(bones: List<GeoBone>, out: MutableMap<String, FloatArray>) {
        for (bone in bones) {
            out[bone.name] = floatArrayOf(
                bone.rotX, bone.rotY, bone.rotZ,
                bone.posX, bone.posY, bone.posZ,
                bone.scaleX, bone.scaleY, bone.scaleZ
            )
            captureInitial(bone.childBones, out)
        }
    }

    private fun cloneBone(source: GeoBone, parent: GeoBone?): GeoBone {
        val copy = GeoBone(parent, source.name, source.mirror, source.inflate, source.shouldNeverRender(), source.reset)
        copy.pivotX = source.pivotX
        copy.pivotY = source.pivotY
        copy.pivotZ = source.pivotZ
        val init = source.initialSnapshot
        if (init != null) {
            copy.rotX = init.rotX
            copy.rotY = init.rotY
            copy.rotZ = init.rotZ
            copy.posX = init.offsetX
            copy.posY = init.offsetY
            copy.posZ = init.offsetZ
            copy.scaleX = init.scaleX
            copy.scaleY = init.scaleY
            copy.scaleZ = init.scaleZ
        } else {
            copy.rotX = source.rotX
            copy.rotY = source.rotY
            copy.rotZ = source.rotZ
        }
        copy.cubes.addAll(source.cubes)
        for (child in source.childBones) {
            copy.childBones.add(cloneBone(child, copy))
        }
        return copy
    }
}
