package org.lantern.internal.icon

import net.minecraft.resources.ResourceLocation

/** packet 4 下发的一条物品图标定义 */
data class IconSpec(val identifier: String, val texture: String, val type: String)

/**
 * 运行时由贴图生成的物品图标网格，与原版 item/generated 烘焙出的面一一对应，
 * 坐标在模型空间 0..1，UV 对应独立贴图（不进方块图集）。
 * 每个面 [STRIDE] 个 float：法线 xyz，随后 4 个顶点各 x y z u v
 */
class IconMesh(
    val texture: ResourceLocation,
    val handheld: Boolean,
    val data: FloatArray
) {
    val quadCount: Int get() = data.size / STRIDE

    /** 平台层按网格缓存的渲染模型（1.21.1 / 1.20.1 是占位 BakedModel）；网格作废时随之丢弃 */
    @Volatile
    var platformModel: Any? = null

    companion object {
        const val STRIDE = 23
    }
}
