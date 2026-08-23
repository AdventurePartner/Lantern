package org.lantern.internal.network

import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import org.lantern.Lantern
import org.lantern.internal.chat.ChatChannel
import org.lantern.internal.chat.ChatChannelHandler
import org.lantern.internal.handler.EncryptedPackLoader
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.parser.UiParser
import org.lantern.internal.placeholder.PlaceholderStore
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import org.lantern.internal.wrapper.resource.ItemIconResourceWrapperImpl
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.wrapper.BlockModelWrapper
import org.lantern.costume.bone.BoneMapping
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.model.wrapper.AnimationStateMapping
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
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
            10 -> parseBlockModels(obj)
            11 -> parseBlockPositions(obj)
            12 -> parseBlockPositionUpdate(obj)
            13 -> parsePlaceholderUpdate(obj)
            14 -> parseChatChannels(obj)
            99 -> reloadResourcePack()
        }
    }

    private fun parsePlaceholderUpdate(obj: JsonObject) {
        val screenId = obj.get("screen-id")?.asString ?: return
        val valuesObj = obj.getAsJsonObject("values") ?: return
        val values = mutableMapOf<String, String>()
        for ((k, v) in valuesObj.entrySet()) {
            values[k] = v.asString
        }
        val entry = UiScreenStorage.get(screenId) ?: return
        PlaceholderStore.update(screenId, entry.rootWidget, values)
    }

    private fun parseChatChannels(obj: JsonObject) {
        val channelArray = obj.getAsJsonArray("channels") ?: return
        val channels = channelArray.mapNotNull { element ->
            if (!element.isJsonObject) {
                return@mapNotNull null
            }

            val channelObj = element.asJsonObject
            val id = channelObj.get("id")?.asString.orEmpty()
            val displayName = channelObj.get("display-name")?.asString ?: id
            val prefixes = channelObj.getAsJsonArray("prefixes")
                ?.mapNotNull { prefix -> prefix.takeIf { it.isJsonPrimitive }?.asString }
                ?: emptyList()
            val filter = channelObj.getAsJsonArray("filter")
                ?.mapNotNull { filteredId -> filteredId.takeIf { it.isJsonPrimitive }?.asString }
                ?: emptyList()


            ChatChannel(id, displayName, prefixes, filter)
        }
        ChatChannelHandler.setChannels(channels)
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
        RendererHandler.reload()
        ResourceHandler.clearEntityModels()
        val models = obj.getAsJsonArray("models")
        models.map { it as JsonObject }.forEach {
            val name = it.get("name").asString
            RendererHandler.addEntityModel(name, it)
            ResourceHandler.addEntityModelEntry(name, it)
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
        ResourceHandler.clearItemIcons()
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
        val definitions = LinkedHashMap<String, CostumeModelWrapper>()
        costumes.map { it as JsonObject }.forEach {
            val id = it.get("id").asString
            val displayName = it.get("display-name")?.asString ?: id
            val geoPath = it.get("geo").asString
            val texturePath = it.get("texture").asString

            val isHttpTexture = TextureHandler.isHttpUrl(texturePath)
            val geo = IdentifierBridge.of(Lantern.MOD_ID, geoPath)

            val texture: ResourceLocation
            val textureUrl: String?
            if (isHttpTexture) {
                texture = TextureHandler.getTexture(texturePath)
                textureUrl = texturePath
            } else {
                texture = IdentifierBridge.of(Lantern.MOD_ID, texturePath)
                textureUrl = null
            }

            val animationLocation: ResourceLocation
            val animationStates: AnimationStateMapping

            if (it.has("animations") && it.get("animations").isJsonObject) {
                val animationsObj = it.getAsJsonObject("animations")
                val animPath = animationsObj.get("file").asString
                animationLocation = IdentifierBridge.of(Lantern.MOD_ID, animPath)

                val statesObj = animationsObj.getAsJsonObject("states")
                animationStates = AnimationStateMapping(
                    idle = statesObj?.get("idle")?.asString ?: "idle",
                    walk = statesObj?.get("walk")?.asString ?: "walk",
                    attack = statesObj?.get("attack")?.asString,
                    hurt = statesObj?.get("hurt")?.asString,
                    death = statesObj?.get("death")?.asString
                )
            } else {
                animationLocation = IdentifierBridge.of(
                    Lantern.MOD_ID, "animations/costume/default.animation.json"
                )
                animationStates = AnimationStateMapping.default()
            }

            val scale = it.get("scale")?.asFloat ?: 1.0f
            val offsetObj = it.getAsJsonObject("offset")
            val offsetX = offsetObj?.get("x")?.asFloat ?: 0.0f
            val offsetY = offsetObj?.get("y")?.asFloat ?: 0.0f
            val offsetZ = offsetObj?.get("z")?.asFloat ?: 0.0f

            val slot = CostumeSlot.fromString(it.get("slot")?.asString ?: "full_body")
            val boneSyncEnabled = it.get("bone-sync")?.asBoolean ?: true
            val boneMapping = it.getAsJsonObject("bone-mapping")?.let { bm ->
                BoneMapping(
                    head = bm.get("head")?.asString ?: "head",
                    body = bm.get("body")?.asString ?: "body",
                    leftArm = bm.get("left_arm")?.asString ?: "left_arm",
                    rightArm = bm.get("right_arm")?.asString ?: "right_arm",
                    leftLeg = bm.get("left_leg")?.asString ?: "left_leg",
                    rightLeg = bm.get("right_leg")?.asString ?: "right_leg"
                )
            } ?: BoneMapping()

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
                textureUrl = textureUrl,
                slot = slot,
                boneSyncEnabled = boneSyncEnabled,
                boneMapping = boneMapping
            )

            definitions[id] = wrapper
        }
        CostumeHandler.replaceDefinitions(definitions)
        Lantern.logger.info("[Lantern] Received {} costume definitions", costumes.size())
    }

    private fun parseCostumeAssignments(obj: JsonObject) {
        val assignments = obj.getAsJsonArray("assignments")
        assignments?.map { it as JsonObject }?.forEach { entry ->
            val uuid = UUID.fromString(entry.get("uuid").asString)
            if (entry.has("costumes") && entry.get("costumes").isJsonArray) {
                // New multi-slot format: { "uuid": "...", "costumes": [{ "slot": "...", "costume": "..." }] }
                entry.getAsJsonArray("costumes").map { it as JsonObject }.forEach { slotEntry ->
                    val costumeId = slotEntry.get("costume").asString
                    CostumeHandler.assignCostume(uuid, costumeId)
                }
            } else {
                // Legacy format: { "uuid": "...", "costume": "..." } — treat as FULL_BODY
                val costumeId = entry.get("costume").asString
                CostumeHandler.assignCostume(uuid, costumeId)
            }
        }

        val removals = obj.getAsJsonArray("removals")
        removals?.forEach { element ->
            if (element.isJsonPrimitive) {
                // Legacy format: array of UUID strings — remove all slots
                CostumeHandler.removeCostume(UUID.fromString(element.asString))
            } else {
                // New format: { "uuid": "...", "slot": "..." } — remove specific slot (or all if no slot)
                val removalObj = element.asJsonObject
                val uuid = UUID.fromString(removalObj.get("uuid").asString)
                val slotStr = removalObj.get("slot")?.asString
                if (slotStr != null) {
                    CostumeHandler.removeCostume(uuid, CostumeSlot.fromString(slotStr))
                } else {
                    CostumeHandler.removeCostume(uuid)
                }
            }
        }
    }

    private fun parseBlockModels(obj: JsonObject) {
        BlockRendererHandler.clear()
        ResourceHandler.clearBlockModels()
        val blocks = obj.getAsJsonArray("blocks")?.map { it as JsonObject } ?: return
        Lantern.logger.info("[Lantern] Received {} custom block models", blocks.size)
        blocks.forEach {
            val id = it.get("id").asString
            val variation = it.get("custom_variation").asInt

            // GeckoLib 模型资源
            val geoPath = it.get("geo")?.asString ?: return@forEach
            val texturePath = it.get("texture")?.asString ?: return@forEach
            val animationPath = it.get("animation")?.asString?.takeIf { a -> a.isNotBlank() }
            val scale = it.get("scale")?.asFloat ?: 1.0f
            val idleAnimation = it.get("idle_animation")?.asString ?: "idle"
            val blockScale = it.get("block_scale")?.asFloat ?: 1.0f
            val itemOffsetX = it.get("item_offset_x")?.asFloat ?: 0.0f
            val itemOffsetY = it.get("item_offset_y")?.asFloat ?: 0.0f
            val itemOffsetZ = it.get("item_offset_z")?.asFloat ?: 0.0f
            val hardness = it.get("hardness")?.asFloat ?: 1.5f
            val preferredTool = it.get("preferred_tool")?.asString
            val breakSound = it.get("break_sound")?.asString

            val isHttpTexture = TextureHandler.isHttpUrl(texturePath)
            val geo = IdentifierBridge.of(Lantern.MOD_ID, geoPath)
            val texture: ResourceLocation
            val textureUrl: String?
            if (isHttpTexture) {
                texture = TextureHandler.getTexture(texturePath)
                textureUrl = texturePath
            } else {
                texture = IdentifierBridge.of(Lantern.MOD_ID, texturePath)
                textureUrl = null
            }
            val animation = animationPath?.let { p -> IdentifierBridge.of(Lantern.MOD_ID, p) }

            val wrapper = BlockModelWrapper(
                geo, texture, animation, scale, idleAnimation, textureUrl,
                blockScale, itemOffsetX, itemOffsetY, itemOffsetZ,
                hardness, preferredTool, breakSound
            )
            val customModelData = it.get("custom_model_data")?.asInt ?: -1
            BlockRendererHandler.register(variation, id, wrapper, customModelData)

            // 存储到 clientStorage 以便资源重载时恢复
            ResourceHandler.addBlockModelEntry(
                variation, id, geoPath, texturePath, animationPath, scale, idleAnimation, textureUrl, customModelData,
                blockScale, itemOffsetX, itemOffsetY, itemOffsetZ, hardness, preferredTool, breakSound
            )
        }
    }

    private fun parseBlockPositions(obj: JsonObject) {
        BlockRendererHandler.clearPositions()
        val positions = obj.getAsJsonArray("positions") ?: return
        positions.map { it as JsonObject }.forEach {
            val pos = net.minecraft.core.BlockPos(it["x"].asInt, it["y"].asInt, it["z"].asInt)
            val variation = it["v"].asInt
            BlockRendererHandler.addBlockPosition(pos, variation)
        }
        Lantern.logger.info("[Lantern] Received {} block positions", positions.size())
        // 直接标脏，不延迟——已在主线程（context.client().execute 内）
        BlockRendererHandler.markSectionsDirty()
    }

    private fun parseBlockPositionUpdate(obj: JsonObject) {
        val action = obj["action"].asString
        val pos = net.minecraft.core.BlockPos(obj["x"].asInt, obj["y"].asInt, obj["z"].asInt)
        when (action) {
            "add" -> {
                BlockRendererHandler.addBlockPosition(pos, obj["v"].asInt)
                BlockRendererHandler.markSectionDirtyAt(pos)
            }
            "remove" -> {
                BlockRendererHandler.removeBlockPosition(pos)
                BlockRendererHandler.markSectionDirtyAt(pos)
            }
        }
    }

    private fun reloadResourcePack() {
        Minecraft.getInstance().execute { Minecraft.getInstance().reloadResourcePacks(); }
    }
}
