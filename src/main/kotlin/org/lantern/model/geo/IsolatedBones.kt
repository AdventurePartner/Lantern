package org.lantern.model.geo

import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone

/**
 * GeckoLibCache 里的 BakedGeoModel 及其 GeoBone 按模型 ID 全局共享，渲染管线直接遍历
 * 这棵树绘制。由 Lantern 动画内核驱动的模型（实体模型、hostDriven 外观）每个渲染器
 * 要深拷贝一份，否则同名模型的多个实体 / 玩家会互相覆盖姿势。
 * Fabric 1.21.1 / Forge 1.21.1 共用；1.20.1 的 GeoBone 只有逐轴 setter，另有一份
 */
object IsolatedBones {

    class Result(
        val model: BakedGeoModel,
        /** 克隆骨骼的静态初始姿势（rot/pos/scale 各 3 轴），写骨骼时剪辑没驱动到的轴回落到它 */
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
        copy.updatePivot(source.pivotX, source.pivotY, source.pivotZ)
        // 初始变换取 initialSnapshot：共享骨骼的当前值可能已被别的渲染器写过
        val init = source.initialSnapshot
        if (init != null) {
            copy.updateRotation(init.rotX, init.rotY, init.rotZ)
            copy.updatePosition(init.offsetX, init.offsetY, init.offsetZ)
            copy.updateScale(init.scaleX, init.scaleY, init.scaleZ)
        } else {
            copy.updateRotation(source.rotX, source.rotY, source.rotZ)
        }
        // GeoCube 是烘焙好的不可变顶点数据，跨模型共享引用安全
        copy.cubes.addAll(source.cubes)
        for (child in source.childBones) {
            copy.childBones.add(cloneBone(child, copy))
        }
        return copy
    }
}
