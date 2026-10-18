package org.lantern.item.icon

import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.block.model.ItemTransforms
import org.joml.Vector3f

/**
 * 原版 models/item/generated.json 与 handheld.json 的 display 表。
 * 平移按 json 写法给像素值，构造时换算成方块单位（反序列化器同样乘 1/16）；
 * 左手缺省时沿用右手，渲染时由 ItemTransform.apply 镜像
 */
internal object IconTransforms {
    val GENERATED: ItemTransforms = build(handheld = false)
    val HANDHELD: ItemTransforms = build(handheld = true)

    private fun build(handheld: Boolean): ItemTransforms {
        val thirdPersonRight = if (handheld) transform(0f, -90f, 55f, 0f, 4f, 0.5f, 0.85f)
            else transform(0f, 0f, 0f, 0f, 3f, 1f, 0.55f)
        val thirdPersonLeft = if (handheld) transform(0f, 90f, -55f, 0f, 4f, 0.5f, 0.85f) else thirdPersonRight
        val firstPersonRight = transform(0f, -90f, 25f, 1.13f, 3.2f, 1.13f, 0.68f)
        val firstPersonLeft = if (handheld) transform(0f, 90f, -25f, 1.13f, 3.2f, 1.13f, 0.68f) else firstPersonRight
        return ItemTransforms(
            thirdPersonLeft,
            thirdPersonRight,
            firstPersonLeft,
            firstPersonRight,
            transform(0f, 180f, 0f, 0f, 13f, 7f, 1f),
            ItemTransform.NO_TRANSFORM,
            transform(0f, 0f, 0f, 0f, 2f, 0f, 0.5f),
            transform(0f, 180f, 0f, 0f, 0f, 0f, 1f)
        )
    }

    private fun transform(
        rotationX: Float, rotationY: Float, rotationZ: Float,
        translationX: Float, translationY: Float, translationZ: Float,
        scale: Float
    ): ItemTransform = ItemTransform(
        Vector3f(rotationX, rotationY, rotationZ),
        Vector3f(translationX / 16f, translationY / 16f, translationZ / 16f),
        Vector3f(scale, scale, scale)
    )
}
