package org.lantern.costume.attach

import org.lantern.animation.PosedBones
import software.bernie.geckolib.cache.model.BakedGeoModel
import software.bernie.geckolib.cache.model.GeoBone

/**
 * hostDriven 整替外观在本帧的骨架：烘焙好的骨骼树（GeckoLib 缓存，只读）+ AnimationHost 的姿势表。
 * 渲染过程之外跟骨的消费方都从这里取，替代 5.3 时直接读写好姿势的 GeoBone
 */
class HostSkeleton(
    val model: BakedGeoModel,
    val pose: Map<String, FloatArray>?
) {
    fun bone(name: String): GeoBone? = model.getBone(name).orElse(null)

    /** 本帧姿态下某骨的 rot/pos/scale 九值，见 [PosedBones.values] */
    fun values(bone: GeoBone): FloatArray = PosedBones.values(bone, pose)
}
