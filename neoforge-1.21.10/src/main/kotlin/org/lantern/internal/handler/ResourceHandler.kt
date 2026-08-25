package org.lantern.internal.handler

import com.google.gson.JsonObject
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.costume.handler.CostumeHandler
import org.lantern.internal.storage.BlockModelEntry
import org.lantern.internal.storage.ClientStorage
import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import org.lantern.internal.wrapper.resource.IResourceWrapper
import org.lantern.internal.wrapper.resource.ByteArrayResourceWrapper
import org.lantern.internal.wrapper.resource.ItemIconResourceWrapperImpl
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.model.wrapper.BlockModelWrapper
import org.lantern.platform.IdentifierBridge
import java.util.concurrent.ConcurrentHashMap

object ResourceHandler {
    private val clientStorage = ClientStorage()
    private val resources = ConcurrentHashMap<ResourceLocation, IResourceWrapper>()
    private val encryptedResources: MutableSet<ResourceLocation> = ConcurrentHashMap.newKeySet()
    private val localResources: MutableSet<ResourceLocation> = ConcurrentHashMap.newKeySet()
    private val generatedResources: MutableSet<ResourceLocation> = ConcurrentHashMap.newKeySet()
    private val itemIcons = ConcurrentHashMap<Int, String>()
    private val blockModels = ConcurrentHashMap<Int, String>()
    private val characters = ConcurrentHashMap<Char, CharacterWrapper>()
    private val keyboards = ConcurrentHashMap<String, KeyWrapper>()

    fun listDynamicResources(namespace: String, path: String): Map<ResourceLocation, IResourceWrapper> =
        resources.filter { (location, _) ->
            location.namespace == namespace && location.path.startsWith(path)
        }

    fun getResource(location: ResourceLocation): IResourceWrapper? = resources[location]

    fun resourceSnapshot(): Map<ResourceLocation, IResourceWrapper> = resources.toMap()

    fun addEncryptedPackResource(location: ResourceLocation, wrapper: IResourceWrapper) {
        addPackResource(location, wrapper, encryptedResources)
    }

    fun clearEncryptedPackResources() {
        clearResources(encryptedResources)
    }

    fun addLocalPackResource(location: ResourceLocation, wrapper: IResourceWrapper) {
        addPackResource(location, wrapper, localResources)
    }

    fun clearLocalPackResources() {
        clearResources(localResources)
    }

    fun clearItemIcons() {
        itemIcons.clear()
        clientStorage.itemIcons.clear()
        clearResources(generatedResources)
    }

    fun addItemIcon(
        customModelData: Int,
        identifier: String,
        resource: ItemIconResourceWrapperImpl
    ) {
        itemIcons[customModelData] = identifier
        clientStorage.itemIcons[customModelData] = Triple(
            identifier,
            resource.getTexturePath(),
            resource.getType()
        )
        addGeneratedResource(resource.getResourceLocation(), resource)
        addGeneratedResource(resource.getSecondaryResourceLocation(), resource)
        val clientItemLocation = IdentifierBridge.of(Lantern.MOD_ID, "items/$identifier.json")
        val clientItem = JsonObject().apply {
            add("model", JsonObject().apply {
                addProperty("type", "minecraft:model")
                addProperty("model", "${Lantern.MOD_ID}:item/$identifier")
            })
        }
        addGeneratedResource(
            clientItemLocation,
            ByteArrayResourceWrapper(clientItemLocation, clientItem.toString().toByteArray(Charsets.UTF_8))
        )
    }

    fun getItemIcon(customModelData: Int): String? = itemIcons[customModelData]

    fun getItemIcons(): Map<Int, String> = itemIcons.toMap()

    fun clearBlockModels() {
        blockModels.clear()
        clientStorage.blockModels.clear()
    }

    fun addBlockModelEntry(
        variation: Int,
        identifier: String,
        geo: String,
        texture: String,
        animation: String?,
        scale: Float,
        idleAnimation: String,
        textureUrl: String?,
        customModelData: Int = -1,
        blockScale: Float = 1.0f,
        itemOffsetX: Float = 0.0f,
        itemOffsetY: Float = 0.0f,
        itemOffsetZ: Float = 0.0f,
        hardness: Float = 1.5f,
        preferredTool: String? = null,
        breakSound: String? = null
    ) {
        blockModels[variation] = identifier
        clientStorage.blockModels[variation] = BlockModelEntry(
            identifier,
            geo,
            texture,
            animation,
            scale,
            idleAnimation,
            textureUrl,
            customModelData,
            blockScale,
            itemOffsetX,
            itemOffsetY,
            itemOffsetZ,
            hardness,
            preferredTool,
            breakSound
        )
    }

    fun addEntityModelEntry(name: String, data: JsonObject) {
        clientStorage.entityModels[name] = data.deepCopy()
    }

    fun clearEntityModels() {
        clientStorage.entityModels.clear()
    }

    fun getCharacterWrapper(character: Char): CharacterWrapper? = characters[character]

    fun addCharacterWrapper(character: Char, wrapper: CharacterWrapper) {
        characters[character] = wrapper
        clientStorage.characters[character] = wrapper
    }

    fun getAdjustedWidth(font: Font, text: FormattedText): Int {
        val vanillaWidth = font.width(text)
        val extraWidth = text.string.sumOf { character ->
            val wrapper = characters[character] ?: return@sumOf 0.0
            (wrapper.wide - font.width(character.toString())).toDouble()
        }
        return (vanillaWidth + extraWidth).toInt()
    }

    fun getKeyboard(key: String): KeyWrapper? = keyboards[key]

    fun getKeyboards(): Map<String, KeyWrapper> = keyboards

    fun addKeyboard(key: String, wrapper: KeyWrapper) {
        keyboards[key] = wrapper
        clientStorage.keyboards[key] = wrapper
    }

    fun reload() {
        Lantern.logger.info("[Lantern] Reloading dynamic resources")
        rebuild()
    }

    fun rebuild() {
        characters.clear()
        keyboards.clear()
        itemIcons.clear()
        blockModels.clear()
        resources.clear()
        encryptedResources.clear()
        localResources.clear()
        generatedResources.clear()

        EncryptedPackLoader.reloadEncryptedPacks()
        LocalPackLoader.reload()
        characters.putAll(clientStorage.characters)
        keyboards.putAll(clientStorage.keyboards)

        clientStorage.itemIcons.forEach { (customModelData, definition) ->
            val (identifier, texturePath, type) = definition
            addItemIcon(
                customModelData,
                identifier,
                ItemIconResourceWrapperImpl(identifier, texturePath, type)
            )
        }

        BlockRendererHandler.clear()
        clientStorage.blockModels.forEach { (variation, entry) ->
            blockModels[variation] = entry.identifier
            BlockRendererHandler.register(variation, entry.identifier, entry.toWrapper(), entry.customModelData)
        }
        BlockRendererHandler.markSectionsDirty()

        RendererHandler.reload()
        clientStorage.entityModels.forEach(RendererHandler::addEntityModel)
    }

    fun clearSession() {
        EncryptedPackLoader.clearPassword()
        // 本地包/加密包是客户端本地资源，退出服务器时保留；
        // 清空会导致重连后 pkt 99 的重载向 GeckoLib 提供空包，模型全部消失
        clearResources(generatedResources)
        itemIcons.clear()
        blockModels.clear()
        characters.clear()
        keyboards.clear()
        clientStorage.characters.clear()
        clientStorage.keyboards.clear()
        clientStorage.itemIcons.clear()
        clientStorage.blockModels.clear()
        clientStorage.entityModels.clear()
        RendererHandler.reload()
        BlockRendererHandler.clear()
        BlockRendererHandler.clearPositions()
        CostumeHandler.reload()
    }

    private fun addPackResource(
        location: ResourceLocation,
        wrapper: IResourceWrapper,
        owner: MutableSet<ResourceLocation>
    ) {
        resources[location] = wrapper
        owner.add(location)
        geckoLibAlias(location)?.let { alias ->
            resources[alias] = wrapper
            owner.add(alias)
        }
    }

    private fun addGeneratedResource(location: ResourceLocation, wrapper: IResourceWrapper) {
        resources[location] = wrapper
        generatedResources.add(location)
    }

    private fun clearResources(locations: MutableSet<ResourceLocation>) {
        locations.forEach(resources::remove)
        locations.clear()
    }

    private fun geckoLibAlias(location: ResourceLocation): ResourceLocation? {
        val aliasPath = when {
            location.path.startsWith("geo/") ->
                "geckolib/models/${location.path.removePrefix("geo/")}"
            location.path.startsWith("animations/") ->
                "geckolib/animations/${location.path.removePrefix("animations/")}"
            else -> return null
        }
        return IdentifierBridge.of(location.namespace, aliasPath)
    }

    private fun BlockModelEntry.toWrapper(): BlockModelWrapper {
        val remoteTexture = TextureHandler.isHttpUrl(texture)
        return BlockModelWrapper(
            modelLocation = IdentifierBridge.of(Lantern.MOD_ID, geo),
            textureLocation = if (remoteTexture) {
                TextureHandler.getTexture(texture)
            } else {
                IdentifierBridge.of(Lantern.MOD_ID, texture)
            },
            animationLocation = animation?.takeIf(String::isNotBlank)?.let {
                IdentifierBridge.of(Lantern.MOD_ID, it)
            },
            scale = scale,
            idleAnimation = idleAnimation,
            textureUrl = textureUrl,
            blockScale = blockScale,
            itemOffsetX = itemOffsetX,
            itemOffsetY = itemOffsetY,
            itemOffsetZ = itemOffsetZ,
            hardness = hardness,
            preferredTool = preferredTool,
            breakSound = breakSound
        )
    }
}
