package org.lantern.model.wrapper

import net.minecraft.resources.ResourceLocation

/**
 * 自定义方块模型配置包装类。
 * 与 CustomModelWrapper（实体）对应，用于 GeckoLib 方块渲染。
 *
 * @param modelLocation geo 模型资源路径
 * @param textureLocation 纹理资源路径
 * @param animationLocation 动画资源路径（静态方块可为 null）
 * @param scale 物品形态缩放（手持/GUI/地面等）
 * @param idleAnimation 待机动画名称
 * @param textureUrl 远程纹理 URL（优先于 textureLocation）
 * @param blockScale 放置后 in-world 渲染缩放
 * @param itemOffsetX 物品槽位图标 X 偏移
 * @param itemOffsetY 物品槽位图标 Y 偏移
 * @param itemOffsetZ 物品槽位图标 Z 偏移
 * @param hardness 挖掘硬度（越大越慢，<0 不可破坏）
 * @param preferredTool 加速挖掘的工具类型（pickaxe/axe/shovel）
 * @param breakSound 破坏音效（MC sound event ID，空则使用默认）
 */
data class BlockModelWrapper(
    val modelLocation: ResourceLocation,
    val textureLocation: ResourceLocation,
    val animationLocation: ResourceLocation? = null,
    val scale: Float = 1.0f,
    val idleAnimation: String = "idle",
    val textureUrl: String? = null,
    val blockScale: Float = 1.0f,
    val itemOffsetX: Float = 0.0f,
    val itemOffsetY: Float = 0.0f,
    val itemOffsetZ: Float = 0.0f,
    val hardness: Float = 1.5f,
    val preferredTool: String? = null,
    val breakSound: String? = null
)
