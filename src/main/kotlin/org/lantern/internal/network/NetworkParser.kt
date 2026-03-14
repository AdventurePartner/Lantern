package org.lantern.internal.network

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import org.lantern.Lantern
import org.lantern.internal.handler.EncryptedPackLoader
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.parser.UiParser
import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import org.lantern.internal.wrapper.resource.ItemIconResourceWrapperImpl
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.model.wrapper.AnimationStateMapping
import net.minecraft.resources.ResourceLocation
import java.util.UUID

object NetworkParser {

    fun parse(packetId: Int, obj: JsonObject) {
        when (packetId) {
            1 -> parseCharacters(obj)
            2 -> parseEntityModels(obj)
            3 -> parseKeyboard(obj)
            4 -> parseCustomItemIcon(obj)
            5 -> parseUiScreens(obj)
            6 -> openGuiScreen(obj)
            7 -> handleResourcePackKey(obj)
            8 -> parseCostumes(obj)
            9 -> parseCostumeAssignments(obj)
            99 -> reloadResourcePack()
        }
    }

    private fun parseUiScreens(obj: JsonObject) {
        val screens = obj.getAsJsonArray("screens") ?: return
        UiParser.parseScreens(screens)
    }

    private fun openGuiScreen(obj: JsonObject) {
        val screenId = obj.get("screen-id")?.asString ?: return
        val entry = org.lantern.internal.storage.UiScreenStorage.get(screenId) ?: return
        if (entry.type != org.lantern.internal.storage.ScreenType.GUI) return
        Minecraft.getInstance().execute {
            Minecraft.getInstance().setScreen(
                org.lantern.uix.canvas.impl.GuiCanvas(screenId, entry.rootWidget)
            )
        }
    }

    private fun parseEntityModels(obj: JsonObject) {
        val models = obj.getAsJsonArray("models")
        models.map { it as JsonObject }.forEach {
            val name = it.get("name").asString
            RendererHandler.addEntityModel(name, it)
        }
    }

    private fun parseCharacters(obj: JsonObject) {
        val array = obj.getAsJsonArray("characters")
        array.map { it as JsonObject }.forEach {
            val character = it.get("character").asString.first()
            ResourceHandler.addCharacterWrapper(character, CharacterWrapper.parse(it))
        }
    }

    private fun parseKeyboard(obj: JsonObject) {
        obj.getAsJsonArray("keys").map { it as JsonObject }.forEach {
            val key = it.get("key").asString
            val press = it.get("press")?.asBoolean ?: false
            ResourceHandler.addKeyboard(key.lowercase(), KeyWrapper(press))
        }
    }

    private fun parseCustomItemIcon(obj: JsonObject) {
        val icons = obj.getAsJsonArray("icons").map { it as JsonObject }
        Lantern.logger.info("[Lantern] Received {} custom item icons", icons.size)
        icons.forEach {
            val customModelData = it.get("data").asInt
            val rawIdentifier = it.get("identifier").asString
            // 提取纯名称部分：
            // 1. 去掉命名空间前缀（如 "lantern:"）
            // 2. 去掉 "item/" 前缀（如果存在）
            var identifier = if (rawIdentifier.contains(":")) {
                rawIdentifier.substringAfter(":")
            } else {
                rawIdentifier
            }
            // 去掉 "item/" 前缀
            if (identifier.startsWith("item/")) {
                identifier = identifier.substringAfter("item/")
            }
            // 处理 texture 路径
            var texture = it.get("texture")?.asString ?: "${Lantern.MOD_ID}:item/$identifier"
            // 修复 texture 路径中可能存在的重复问题（如 "lantern:item/item/xxx"）
            if (texture.contains("/item/item/")) {
                texture = texture.replace("/item/item/", "/item/")
            }
            // 如果 texture 包含类似 "namespace:path/namespace:path/xxx" 的重复格式，修复它
            val colonCount = texture.count { it == ':' }
            if (colonCount > 1) {
                val lastColon = texture.lastIndexOf(':')
                val namespace = texture.substring(0, texture.indexOf(':'))
                val path = texture.substring(lastColon + 1)
                texture = "$namespace:$path"
            }
            Lantern.logger.debug("[Lantern] Parsing icon: data={}, identifier={}, texture={}",
                customModelData, identifier, texture)
            val type = it.get("type")?.asString ?: "generated"
            val res = ItemIconResourceWrapperImpl(identifier, texture, type)
            ResourceHandler.addItemIcon(customModelData, identifier, res)
        }
    }

    private fun handleResourcePackKey(obj: JsonObject) {
        val key = obj.get("key")?.asString ?: return
        if (key.isBlank()) return
        Lantern.logger.info("[Lantern] Received resource pack key, loading encrypted packs...")
        EncryptedPackLoader.loadEncryptedPacks(key)
    }

    private fun parseCostumes(obj: JsonObject) {
        val costumes = obj.getAsJsonArray("costumes") ?: return
        costumes.map { it as JsonObject }.forEach {
            val id = it.get("id").asString
            val displayName = it.get("display-name")?.asString ?: id
            val geoPath = it.get("geo").asString
            val texturePath = it.get("texture").asString

            val isHttpTexture = TextureHandler.isHttpUrl(texturePath)
            val geo = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, geoPath)

            val texture: ResourceLocation
            val textureUrl: String?
            if (isHttpTexture) {
                texture = TextureHandler.getTexture(texturePath)
                textureUrl = texturePath
            } else {
                texture = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, texturePath)
                textureUrl = null
            }

            val animationLocation: ResourceLocation
            val animationStates: AnimationStateMapping

            if (it.has("animations") && it.get("animations").isJsonObject) {
                val animationsObj = it.getAsJsonObject("animations")
                val animPath = animationsObj.get("file").asString
                animationLocation = ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, animPath)

                val statesObj = animationsObj.getAsJsonObject("states")
                animationStates = AnimationStateMapping(
                    idle = statesObj?.get("idle")?.asString ?: "idle",
                    walk = statesObj?.get("walk")?.asString ?: "walk",
                    attack = statesObj?.get("attack")?.asString,
                    hurt = statesObj?.get("hurt")?.asString,
                    death = statesObj?.get("death")?.asString
                )
            } else {
                animationLocation = ResourceLocation.fromNamespaceAndPath(
                    Lantern.MOD_ID, "animations/costume/default.animation.json"
                )
                animationStates = AnimationStateMapping.default()
            }

            val scale = it.get("scale")?.asFloat ?: 1.0f
            val offsetObj = it.getAsJsonObject("offset")
            val offsetX = offsetObj?.get("x")?.asFloat ?: 0.0f
            val offsetY = offsetObj?.get("y")?.asFloat ?: 0.0f
            val offsetZ = offsetObj?.get("z")?.asFloat ?: 0.0f

            val wrapper = CostumeModelWrapper(
                id = id,
                displayName = displayName,
                modelLocation = geo,
                textureLocation = texture,
                animationLocation = animationLocation,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                animationStates = animationStates,
                textureUrl = textureUrl
            )

            CostumeHandler.addCostume(id, wrapper)
        }
        Lantern.logger.info("[Lantern] Received {} costume definitions", costumes.size())
    }

    private fun parseCostumeAssignments(obj: JsonObject) {
        val assignments = obj.getAsJsonArray("assignments")
        assignments?.map { it as JsonObject }?.forEach {
            val uuid = UUID.fromString(it.get("uuid").asString)
            val costumeId = it.get("costume").asString
            CostumeHandler.assignCostume(uuid, costumeId)
        }

        val removals = obj.getAsJsonArray("removals")
        removals?.forEach {
            val uuid = UUID.fromString(it.asString)
            CostumeHandler.removeCostume(uuid)
        }
    }

    private fun reloadResourcePack() {
        Minecraft.getInstance().execute { Minecraft.getInstance().reloadResourcePacks(); }
    }
}
