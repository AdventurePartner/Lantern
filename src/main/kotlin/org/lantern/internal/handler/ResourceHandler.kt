package org.lantern.internal.handler

import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.internal.storage.ClientStorage
import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import org.lantern.internal.wrapper.resource.IResourceWrapper
import org.lantern.internal.wrapper.resource.ItemIconResourceWrapperImpl
import java.util.concurrent.ConcurrentHashMap

/*
 * 资源处理器, 用于缓存各个模块资源
 * 当客户端资源重载时清空重载
 */
object ResourceHandler {
    private val clientStorage = ClientStorage()

    // 动态资源关联集(key[IResourceWrapper#ResourceLocation], value[IResourceWrapper])
    private val dynamicResourceLinked = ConcurrentHashMap<ResourceLocation, IResourceWrapper>()

    // 动态资源缓存集(key[identifier], value[IResourceWrapper])
    private val dynamicResources = ConcurrentHashMap<String, IResourceWrapper>()

    // 自定义图标缓存(key[customModelData], value[identifier])
    private val itemCustomIcons = ConcurrentHashMap<Int, String>()
    private val characterWrappers = ConcurrentHashMap<Char, CharacterWrapper>()
    private val keyboards = ConcurrentHashMap<String, KeyWrapper>()

    /**
     * 获取指定动态资源路径下的所有资源
     *
     * @param namespace 命名空间
     * @param path 资源路径
     */
    fun listDynamicResources(namespace: String, path: String): Map<ResourceLocation, IResourceWrapper> {
        if (namespace != "lantern") return emptyMap()
        return dynamicResourceLinked.filter { (k, v) -> k.namespace == namespace && k.path.startsWith(path) }
    }

    fun getResource(rl: ResourceLocation): IResourceWrapper? {
        return dynamicResourceLinked[rl]
    }

    fun getResource(identifier: String): IResourceWrapper? {
        return dynamicResources[identifier]
    }

    fun getItemCustomIcons(): Map<Int, ModelResourceLocation> {
        val result = itemCustomIcons.mapValues { (_, identifier) ->
            // 标准化标识符：移除 item/ 前缀（ModelResourceLocation.inventory 会自动添加）
            val normalizedPath = if (identifier.startsWith("item/")) {
                identifier.substringAfter("item/")
            } else {
                identifier
            }
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, normalizedPath))
        }
        Lantern.logger.debug("[Lantern] getItemCustomIcons returning {} entries", result.size)
        return result
    }

    fun getItemIcon(customModeLData: Int): String? {
        return itemCustomIcons[customModeLData]
    }

    /**
     * 根据 ModelBakery 传入的模型路径（如 "item/custom_wrapper"）查找对应纹理路径。
     * Fabric ModelLoadingPlugin.resolveModel() 的 ctx.id().path 为 "item/{identifier}"。
     */
    fun getTextureByModelPath(modelPath: String): String? {
        val name = if (modelPath.startsWith("item/")) modelPath.removePrefix("item/") else modelPath
        return clientStorage.itemIcons.values
            .firstOrNull { (identifier, _) -> identifier == name }
            ?.second
    }

    /**
     * 获取所有动态纹理路径
     * 用于在纹理图集构建时注册精灵
     */
    fun getDynamicTextures(): Set<String> {
        return clientStorage.itemIcons.values.map { (_, texturePath) -> texturePath }.toSet()
    }

    fun addItemIcon(customModeLData: Int, identifier: String, res: ItemIconResourceWrapperImpl) {
        itemCustomIcons[customModeLData] = identifier
        dynamicResources["customIcons_$identifier"] = res
        // 注册主路径 (models/item/)
        dynamicResourceLinked[res.getResourceLocation()] = res
        // 注册备用路径 (models/)
        dynamicResourceLinked[res.getSecondaryResourceLocation()] = res
        // 保存到 clientStorage 以便 rebuild 时恢复
        clientStorage.itemIcons[customModeLData] = identifier to res.getTexturePath()
        Lantern.logger.info("[Lantern] Registered item icon: customModelData={}, identifier={}", customModeLData, identifier)
    }

    fun getCharacterWrapper(char: Char): CharacterWrapper? {
        return characterWrappers[char]
    }

    fun addCharacterWrapper(char: Char, wrapper: CharacterWrapper) {
        characterWrappers[char] = wrapper
        clientStorage.characters[char] = wrapper
    }

    fun getKeyboard(key: String): KeyWrapper? {
        return keyboards[key]
    }

    fun getKeyboards(): Map<String, KeyWrapper> {
        return keyboards.toMap()
    }

    fun addKeyboard(key: String, wrapper: KeyWrapper) {
        keyboards[key] = wrapper
        clientStorage.keyboards[key] = wrapper
    }

    /**
     * 当资源管理器重载资源时执行，用来释放 Lantern 加载的内部资源
     */
    fun reload() {
        Lantern.logger.info("[Lantern] Reloading resources")
        rebuild()
    }

    fun rebuild() {
        Lantern.logger.debug("[Lantern] rebuild: START - itemCustomIcons={}", itemCustomIcons.size)
        // 清空当前临时缓存
        characterWrappers.clear()
        keyboards.clear()
        itemCustomIcons.clear()
        dynamicResources.clear()
        dynamicResourceLinked.clear()
        // 重新载入资源
        characterWrappers.putAll(clientStorage.characters)
        keyboards.putAll(clientStorage.keyboards)
        // 恢复 item icons
        clientStorage.itemIcons.forEach { (customModelData, pair) ->
            val (identifier, texturePath) = pair
            val res = ItemIconResourceWrapperImpl(identifier, texturePath)
            itemCustomIcons[customModelData] = identifier
            dynamicResources["customIcons_$identifier"] = res
            dynamicResourceLinked[res.getResourceLocation()] = res
            dynamicResourceLinked[res.getSecondaryResourceLocation()] = res
            Lantern.logger.debug("[Lantern] rebuild: restored icon customModelData={}", customModelData)
        }
        Lantern.logger.debug("[Lantern] rebuild: END - itemCustomIcons={}", itemCustomIcons.size)
    }
}
