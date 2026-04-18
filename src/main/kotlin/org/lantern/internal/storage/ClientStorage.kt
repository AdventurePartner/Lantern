package org.lantern.internal.storage

import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap

class ClientStorage {
    val characters = ConcurrentHashMap<Char, CharacterWrapper>()
    val keyboards = mutableMapOf<String, KeyWrapper>()
    // 存储 itemIcons: customModelData -> (identifier, texturePath, type)
    val itemIcons = ConcurrentHashMap<Int, Triple<String, String, String>>()
    // 存储 blockModels: customVariation -> (identifier, parent, textures)
    val blockModels = ConcurrentHashMap<Int, BlockModelEntry>()
    // 存储 entityModels: name -> JsonObject (原始服务端数据)
    val entityModels = ConcurrentHashMap<String, JsonObject>()

}

/**
 * 方块模型存储条目（GeckoLib 渲染）
 * @param identifier 方块标识符
 * @param geo geo 模型路径
 * @param texture 纹理路径
 * @param animation 动画路径（静态方块可为 null）
 * @param scale 物品形态缩放
 * @param idleAnimation 待机动画名称
 * @param textureUrl 远程纹理 URL（可选）
 * @param customModelData CustomModelData 值
 * @param blockScale 放置后渲染缩放
 * @param itemOffsetX 物品 X 偏移
 * @param itemOffsetY 物品 Y 偏移
 * @param itemOffsetZ 物品 Z 偏移
 * @param hardness 挖掘硬度
 * @param preferredTool 加速工具类型
 * @param breakSound 破坏音效
 */
data class BlockModelEntry(
    val identifier: String,
    val geo: String,
    val texture: String,
    val animation: String? = null,
    val scale: Float = 1.0f,
    val idleAnimation: String = "idle",
    val textureUrl: String? = null,
    val customModelData: Int = -1,
    val blockScale: Float = 1.0f,
    val itemOffsetX: Float = 0.0f,
    val itemOffsetY: Float = 0.0f,
    val itemOffsetZ: Float = 0.0f,
    val hardness: Float = 1.5f,
    val preferredTool: String? = null,
    val breakSound: String? = null
)