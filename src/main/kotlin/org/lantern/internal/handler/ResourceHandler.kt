package org.lantern.internal.handler

import net.minecraft.client.gui.Font
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.internal.storage.ClientStorage
import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import org.lantern.internal.wrapper.resource.IResourceWrapper
import org.lantern.internal.wrapper.resource.ItemIconResourceWrapperImpl
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.wrapper.BlockModelWrapper
import org.lantern.internal.storage.BlockModelEntry
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.JsonObject
import org.lantern.model.handler.RendererHandler

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

    // 加密资源包加载的资源（便于清除重载）
    private val encryptedPackResources: MutableSet<ResourceLocation> = ConcurrentHashMap.newKeySet()

    // 自定义图标缓存(key[customModelData], value[identifier])
    private val itemCustomIcons = ConcurrentHashMap<Int, String>()
    private val characterWrappers = ConcurrentHashMap<Char, CharacterWrapper>()
    private val keyboards = ConcurrentHashMap<String, KeyWrapper>()

    // 自定义方块模型缓存已移至 BlockRendererHandler，此处仅保留 blockCustomModels 用于 ModelBakeryMixin
    private val blockCustomModels = ConcurrentHashMap<Int, String>()

    /**
     * 获取指定动态资源路径下的所有资源
     *
     * @param namespace 命名空间
     * @param path 资源路径
     */
    fun listDynamicResources(namespace: String, path: String): Map<ResourceLocation, IResourceWrapper> {
        return dynamicResourceLinked.filter { (k, _) -> k.namespace == namespace && k.path.startsWith(path) }
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
            .firstOrNull { (identifier, _, _) -> identifier == name }
            ?.second
    }

    /**
     * 获取所有动态纹理路径
     * 用于在纹理图集构建时注册精灵
     */
    fun getDynamicTextures(): Set<String> {
        return clientStorage.itemIcons.values.map { (_, texturePath, _) -> texturePath }.toSet()
    }

    fun addEncryptedPackResource(rl: ResourceLocation, wrapper: IResourceWrapper) {
        dynamicResourceLinked[rl] = wrapper
        encryptedPackResources.add(rl)
    }

    fun clearEncryptedPackResources() {
        encryptedPackResources.forEach { rl ->
            dynamicResourceLinked.remove(rl)
        }
        encryptedPackResources.clear()
    }

    fun clearItemIcons() {
        itemCustomIcons.clear()
        clientStorage.itemIcons.clear()
        dynamicResources.keys.removeIf { it.startsWith("customIcons_") }
    }

    fun addItemIcon(customModeLData: Int, identifier: String, res: ItemIconResourceWrapperImpl) {
        itemCustomIcons[customModeLData] = identifier
        dynamicResources["customIcons_$identifier"] = res
        // 注册主路径 (models/item/)
        dynamicResourceLinked[res.getResourceLocation()] = res
        // 注册备用路径 (models/)
        dynamicResourceLinked[res.getSecondaryResourceLocation()] = res
        // 保存到 clientStorage 以便 rebuild 时恢复
        clientStorage.itemIcons[customModeLData] = Triple(identifier, res.getTexturePath(), res.getType())
        Lantern.logger.info("[Lantern] Registered item icon: customModelData={}, identifier={}", customModeLData, identifier)
    }

    // ========== Block Models (GeckoLib) ==========

    fun getBlockCustomModels(): Map<Int, ModelResourceLocation> {
        return blockCustomModels.mapValues { (_, identifier) ->
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "block/$identifier"))
        }
    }

    fun getBlockModelIdentifier(customVariation: Int): String? {
        return blockCustomModels[customVariation]
    }

    fun clearBlockModels() {
        blockCustomModels.clear()
        clientStorage.blockModels.clear()
    }

    fun addEntityModelEntry(name: String, data: JsonObject) {
        clientStorage.entityModels[name] = data
    }

    fun clearEntityModels() {
        clientStorage.entityModels.clear()
    }

    /**
     * 存储方块模型信息到 clientStorage，以便资源重载时恢复。
     * 实际渲染由 BlockRendererHandler 管理。
     */
    fun addBlockModelEntry(
        variation: Int, identifier: String,
        geo: String, texture: String, animation: String?,
        scale: Float, idleAnimation: String, textureUrl: String?,
        customModelData: Int = -1,
        blockScale: Float = 1.0f,
        itemOffsetX: Float = 0.0f, itemOffsetY: Float = 0.0f, itemOffsetZ: Float = 0.0f,
        hardness: Float = 1.5f, preferredTool: String? = null, breakSound: String? = null
    ) {
        blockCustomModels[variation] = identifier
        clientStorage.blockModels[variation] = BlockModelEntry(
            identifier, geo, texture, animation, scale, idleAnimation, textureUrl, customModelData,
            blockScale, itemOffsetX, itemOffsetY, itemOffsetZ, hardness, preferredTool, breakSound
        )
    }

    fun getCharacterWrapper(char: Char): CharacterWrapper? {
        return characterWrappers[char]
    }

    fun addCharacterWrapper(char: Char, wrapper: CharacterWrapper) {
        characterWrappers[char] = wrapper
        clientStorage.characters[char] = wrapper
    }

    /**
     * 计算包含自定义图标字符的文本真实渲染宽度。
     * Font.width() 不知道 CharacterWrapper 的 wide 值，这里补上差值。
     */
    fun getAdjustedWidth(font: Font, text: FormattedText): Int {
        val width = font.width(text)
        val plain = text.getString()
        var extra = 0f
        for (c in plain) {
            val wrapper = characterWrappers[c]
            if (wrapper != null) {
                val glyphWidth = font.width(c.toString())
                extra += wrapper.wide - glyphWidth
            }
        }
        return (width + extra).toInt()
    }

    fun getKeyboard(key: String): KeyWrapper? {
        return keyboards[key]
    }

    fun getKeyboards(): Map<String, KeyWrapper> = keyboards

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
        Lantern.logger.debug("[Lantern] rebuild: START - itemCustomIcons={}, blockCustomModels={}", itemCustomIcons.size, blockCustomModels.size)
        // 清空当前临时缓存
        characterWrappers.clear()
        keyboards.clear()
        itemCustomIcons.clear()
        blockCustomModels.clear()
        dynamicResources.clear()
        dynamicResourceLinked.clear()
        // 重新加载加密资源包
        EncryptedPackLoader.reloadEncryptedPacks()
        // 重新载入资源
        characterWrappers.putAll(clientStorage.characters)
        keyboards.putAll(clientStorage.keyboards)
        // 恢复 item icons
        clientStorage.itemIcons.forEach { (customModelData, triple) ->
            val (identifier, texturePath, type) = triple
            val res = ItemIconResourceWrapperImpl(identifier, texturePath, type)
            itemCustomIcons[customModelData] = identifier
            dynamicResources["customIcons_$identifier"] = res
            dynamicResourceLinked[res.getResourceLocation()] = res
            dynamicResourceLinked[res.getSecondaryResourceLocation()] = res
            Lantern.logger.debug("[Lantern] rebuild: restored icon customModelData={}", customModelData)
        }
        // 恢复 block models（GeckoLib 渲染，通过 BlockRendererHandler 重建）
        BlockRendererHandler.clear()
        clientStorage.blockModels.forEach { (variation, entry) ->
            blockCustomModels[variation] = entry.identifier
            val isHttpTexture = TextureHandler.isHttpUrl(entry.texture)
            val geo = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, entry.geo)
            val texture = if (isHttpTexture) TextureHandler.getTexture(entry.texture)
                else ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, entry.texture)
            val animation = entry.animation?.takeIf { it.isNotBlank() }?.let {
                ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, it)
            }
            val wrapper = BlockModelWrapper(
                geo, texture, animation, entry.scale, entry.idleAnimation, entry.textureUrl,
                entry.blockScale, entry.itemOffsetX, entry.itemOffsetY, entry.itemOffsetZ,
                entry.hardness, entry.preferredTool, entry.breakSound
            )
            BlockRendererHandler.register(variation, entry.identifier, wrapper, entry.customModelData)
            Lantern.logger.debug("[Lantern] rebuild: restored block variation={}", variation)
        }
        // rebuild 后 models 的 wrapper 实例已更新，清空 BE 缓存并标脏 sections 以抑制 vanilla 渲染
        BlockRendererHandler.clearCache()
        BlockRendererHandler.markSectionsDirty()
        // 重置诊断标记，以便跟踪 rebuild 后渲染状态
        BlockRendererHandler.resetDiagnosticFlags()
        // 验证加密包资源可达性：取第一个 block model 的 geo 路径检查
        clientStorage.blockModels.entries.firstOrNull()?.let { (_, entry) ->
            val testRl = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, entry.geo)
            val found = dynamicResourceLinked.containsKey(testRl)
            Lantern.logger.info(
                "[Lantern] rebuild: resource check geo={} found={}, dynamicResourceLinked.size={}, blockPositions={}",
                testRl, found, dynamicResourceLinked.size, BlockRendererHandler.getPositionCount()
            )
        }
        // 恢复 entity models（通过 RendererHandler 重新注册）
        RendererHandler.reload()
        clientStorage.entityModels.forEach { (name, data) ->
            RendererHandler.addEntityModel(name, data)
        }
        Lantern.logger.debug("[Lantern] rebuild: restored {} entity models", clientStorage.entityModels.size)
        Lantern.logger.debug("[Lantern] rebuild: END - itemCustomIcons={}, blockCustomModels={}", itemCustomIcons.size, blockCustomModels.size)
    }
}
