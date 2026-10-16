package org.lantern.animation

import java.util.concurrent.ConcurrentHashMap
import software.bernie.geckolib.animation.AnimationProcessor
import software.bernie.geckolib.cache.`object`.GeoBone

private val boneNameLowerCache = ConcurrentHashMap<String, String>()

/** 骨骼名小写缓存：动画轨道以小写为键，骨骼名数量有限，避免每帧逐骨分配字符串 */
private fun lowerBoneName(name: String): String =
    boneNameLowerCache.getOrPut(name) { name.lowercase() }

/**
 * 将 per-entity 姿势映射写入处理器骨骼（渲染线程，逐实体串行）。
 * [initial] 为该渲染器克隆骨骼的静态初始姿势（见 GenericGeoModel），剪辑未驱动的
 * 骨骼/轴回落到它。只用 setRotX / setPosX / setScaleX 这组 1.21.1 与 1.20.1 的
 * GeckoLib 都有的接口；1.21.10 有同名副本
 */
fun applyPoseToBones(
    processor: AnimationProcessor<*>,
    pose: Map<String, FloatArray>?,
    initial: Map<String, FloatArray>
) {
    val bones = processor.registeredBones
    if (bones.isEmpty() || pose == null) return
    for (registered in bones) {
        val bone = registered as GeoBone
        val init = initial[bone.name] ?: continue
        val v = pose[lowerBoneName(bone.name)]
        bone.setRotX(if (v == null || v[0].isNaN()) init[0] else v[0])
        bone.setRotY(if (v == null || v[1].isNaN()) init[1] else v[1])
        bone.setRotZ(if (v == null || v[2].isNaN()) init[2] else v[2])
        bone.setPosX(if (v == null || v[3].isNaN()) init[3] else v[3])
        bone.setPosY(if (v == null || v[4].isNaN()) init[4] else v[4])
        bone.setPosZ(if (v == null || v[5].isNaN()) init[5] else v[5])
        bone.setScaleX(if (v == null || v[6].isNaN()) init[6] else v[6])
        bone.setScaleY(if (v == null || v[7].isNaN()) init[7] else v[7])
        bone.setScaleZ(if (v == null || v[8].isNaN()) init[8] else v[8])
    }
}
