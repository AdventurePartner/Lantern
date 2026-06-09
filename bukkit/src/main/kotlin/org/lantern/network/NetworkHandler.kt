package org.lantern.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.World
import org.lantern.handler.CustomBlockTracker
import org.lantern.LanternPlugin
import org.lantern.cache.BlockModelCache
import org.lantern.cache.CostumeCache
import org.lantern.cache.ItemIconCache
import org.lantern.cache.KeyCache
import org.lantern.config.Configurations
import org.lantern.config.UiConfigurations
import org.lantern.handler.CacheHandler
import org.lantern.handler.CostumeAssignmentHandler
import org.lantern.util.JsonUtil
import org.lantern.util.TextUtil.colorify
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger


object NetworkHandler {

    // Cached serialized bytes for static (non-per-player) packets
    private var cachedCharactersBytes: ByteArray? = null
    private var cachedModelsBytes: ByteArray? = null
    private var cachedKeysBytes: ByteArray? = null
    private var cachedItemIconsBytes: ByteArray? = null
    private var cachedScreensBytes: ByteArray? = null
    private var cachedCostumesBytes: ByteArray? = null
    private var cachedBlockModelsBytes: ByteArray? = null
    private var cachedResourcePackKeyBytes: ByteArray? = null
    private const val MAIN_CHANNEL = "lantern:main"
    private const val S2C_JSON_PACKET_TYPE = 0
    private const val S2C_CHUNK_PACKET_TYPE = 2
    private const val MAX_PLUGIN_MESSAGE_BYTES = 32766
    private const val CHUNK_HEADER_BYTES = 21
    private const val MAX_CHUNK_PAYLOAD_BYTES = 32_000
    private val chunkMessageIds = AtomicInteger()


    fun invalidateCache() {
        cachedCharactersBytes = null
        cachedModelsBytes = null
        cachedKeysBytes = null
        cachedItemIconsBytes = null
        cachedScreensBytes = null
        cachedCostumesBytes = null
        cachedBlockModelsBytes = null
        cachedResourcePackKeyBytes = null
    }

    private fun serializePacket(internalPacketId: Int, obj: JsonObject): ByteArray {
        ByteArrayOutputStream().use { byteStream ->
            DataOutputStream(byteStream).use { dataStream ->
                val str = JsonUtil.toJson(obj)
                dataStream.writeByte(S2C_JSON_PACKET_TYPE)
                dataStream.writeInt(internalPacketId)
                dataStream.write(str.toByteArray(Charsets.UTF_8))
            }
            return byteStream.toByteArray()
        }
    }

    private fun sendSerializedPacket(player: Player, bytes: ByteArray) {
        if (bytes.size <= MAX_PLUGIN_MESSAGE_BYTES) {
            sendPluginMessage(player, bytes)
            return
        }

        sendChunkedPacket(player, bytes)
    }

    private fun sendPluginMessage(player: Player, bytes: ByteArray) {
        try {
            player.sendPluginMessage(LanternPlugin.instance, MAIN_CHANNEL, bytes)
        } catch (e: IOException) {
            LanternPlugin.instance.logger.warning("Failed to send message to Forge client: " + e.message)
        }
    }

    private fun sendChunkedPacket(player: Player, bytes: ByteArray) {
        val messageId = chunkMessageIds.incrementAndGet()
        val totalChunks = (bytes.size + MAX_CHUNK_PAYLOAD_BYTES - 1) / MAX_CHUNK_PAYLOAD_BYTES

        for (chunkIndex in 0 until totalChunks) {
            val offset = chunkIndex * MAX_CHUNK_PAYLOAD_BYTES
            val chunkLength = (bytes.size - offset).coerceAtMost(MAX_CHUNK_PAYLOAD_BYTES)
            ByteArrayOutputStream(CHUNK_HEADER_BYTES + chunkLength).use { byteStream ->
                DataOutputStream(byteStream).use { dataStream ->
                    dataStream.writeByte(S2C_CHUNK_PACKET_TYPE)
                    dataStream.writeInt(messageId)
                    dataStream.writeInt(totalChunks)
                    dataStream.writeInt(chunkIndex)
                    dataStream.writeInt(bytes.size)
                    dataStream.writeInt(chunkLength)
                    dataStream.write(bytes, offset, chunkLength)
                }
                sendPluginMessage(player, byteStream.toByteArray())
            }
        }
    }

    fun sendPackets(player: Player) {
        sendResourcePackKey(player)
        sendCharactersPacket(player, Configurations.characters)
        sendEntityModelsPacket(player, Configurations.models)
        sendKeyboards(player, CacheHandler.keys)
        sendItemIcons(player, CacheHandler.itemIcons)
        sendUiScreens(player, UiConfigurations.getScreens())
        sendCostumesPacket(player, CacheHandler.costumes)
        sendCostumeAssignment(player, CostumeAssignmentHandler.getAll())
        sendBlockModels(player, CacheHandler.blockModels)
        sendBlockPositions(player)

        // 延迟一秒发送重载资源数据包
        val relaodRunnable = Runnable { sendReloadResourceManagerPacket(player) }
        Bukkit.getScheduler().runTaskLaterAsynchronously(LanternPlugin.instance, relaodRunnable, 20L)
    }

    fun sendUiScreens(player: Player, screens: List<JsonObject>) {
        val bytes = cachedScreensBytes ?: run {
            val array = JsonArray()
            screens.forEach { array.add(it) }
            val packet = JsonObject()
            packet.add("screens", array)
            serializePacket(5, packet).also { cachedScreensBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendOpenGui(player: Player, screenId: String) {
        val packet = JsonObject()
        packet.addProperty("screen-id", screenId)
        sendPacket(player, 6, packet)
    }

    fun sendCharactersPacket(player: Player, config: FileConfiguration) {
        val bytes = cachedCharactersBytes ?: run {
            val array = JsonArray()
            config.getKeys(false).forEach {
                val section = config.getConfigurationSection(it) ?: return@forEach
                val obj = JsonObject()
                obj.addProperty("character", section.getString("character"))
                obj.addProperty("texture", section.getString("texture"))
                obj.addProperty("width", section.getDouble("width"))
                obj.addProperty("height", section.getDouble("height"))
                obj.addProperty("wide", section.getDouble("wide"))
                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("characters", array)
            serializePacket(1, packet).also { cachedCharactersBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendEntityModelsPacket(player: Player, config: FileConfiguration) {
        val bytes = cachedModelsBytes ?: run {
            val array = JsonArray()
            config.getKeys(false).forEach {
                val section = config.getConfigurationSection(it) ?: return@forEach
                val obj = JsonObject()
                obj.addProperty("name", section.getString("name")!!.colorify())
                obj.addProperty("geo", section.getString("geo"))
                obj.addProperty("texture", section.getString("texture"))

                // 支持新格式 animations 和旧格式 animation
                if (section.contains("animations")) {
                    val animationsSection = section.getConfigurationSection("animations")
                    if (animationsSection != null) {
                        val animationsObj = JsonObject()
                        animationsObj.addProperty("file", animationsSection.getString("file"))

                        // 解析 states
                        if (animationsSection.contains("states")) {
                            val statesSection = animationsSection.getConfigurationSection("states")
                            if (statesSection != null) {
                                val statesObj = JsonObject()
                                statesSection.getString("idle")?.let { s -> statesObj.addProperty("idle", s) }
                                statesSection.getString("walk")?.let { s -> statesObj.addProperty("walk", s) }
                                statesSection.getString("attack")?.let { s -> statesObj.addProperty("attack", s) }
                                statesSection.getString("hurt")?.let { s -> statesObj.addProperty("hurt", s) }
                                statesSection.getString("death")?.let { s -> statesObj.addProperty("death", s) }
                                animationsObj.add("states", statesObj)
                            }
                        }
                        obj.add("animations", animationsObj)
                    }
                } else {
                    // 旧格式
                    obj.addProperty("animation", section.getString("animation"))
                }

                obj.addProperty("scale", section.getDouble("scale"))
                obj.addProperty("height", section.getDouble("height"))
                obj.addProperty("width", section.getDouble("width"))
                obj.addProperty("hidden", section.getBoolean("hidden"))
                obj.addProperty("offset-y", section.getDouble("offset-y"))
                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("models", array)
            serializePacket(2, packet).also { cachedModelsBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendKeyboards(player: Player, keys: Map<String, KeyCache>) {
        val bytes = cachedKeysBytes ?: run {
            val array = JsonArray()
            keys.forEach { (k, v) ->
                val obj = JsonObject()
                obj.addProperty("key", k)
                obj.addProperty("press", v.press)
                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("keys", array)
            serializePacket(3, packet).also { cachedKeysBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendItemIcons(player: Player, itemIcons: Map<Int, ItemIconCache>) {
        val bytes = cachedItemIconsBytes ?: run {
            val array = JsonArray()
            itemIcons.forEach { (k, v) ->
                val obj = JsonObject()
                obj.addProperty("data", k)
                obj.addProperty("identifier", v.identifier)
                obj.addProperty("texture", v.texture)
                obj.addProperty("type", v.type)
                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("icons", array)
            serializePacket(4, packet).also { cachedItemIconsBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendResourcePackKey(player: Player) {
        val bytes = cachedResourcePackKeyBytes ?: run {
            val key = LanternPlugin.instance.config.getString("resource-pack-key") ?: return
            if (key.isBlank()) return
            val packet = JsonObject()
            packet.addProperty("key", key)
            serializePacket(7, packet).also { cachedResourcePackKeyBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendCostumesPacket(player: Player, costumes: Map<String, CostumeCache>) {
        val bytes = cachedCostumesBytes ?: run {
            val array = JsonArray()
            costumes.forEach { (id, cache) ->
                val obj = JsonObject()
                obj.addProperty("id", id)
                obj.addProperty("display-name", cache.displayName)
                obj.addProperty("geo", cache.geo)
                obj.addProperty("texture", cache.texture)

                if (cache.animationFile.isNotBlank()) {
                    val animationsObj = JsonObject()
                    animationsObj.addProperty("file", cache.animationFile)
                    val statesObj = JsonObject()
                    cache.animationStates.forEach { (state, anim) ->
                        statesObj.addProperty(state, anim)
                    }
                    animationsObj.add("states", statesObj)
                    obj.add("animations", animationsObj)
                }

                obj.addProperty("scale", cache.scale)
                val offsetObj = JsonObject()
                offsetObj.addProperty("x", cache.offsetX)
                offsetObj.addProperty("y", cache.offsetY)
                offsetObj.addProperty("z", cache.offsetZ)
                obj.add("offset", offsetObj)

                obj.addProperty("slot", cache.slot)
                obj.addProperty("bone-sync", cache.boneSync)

                val boneMappingObj = JsonObject()
                boneMappingObj.addProperty("head", cache.boneMappingHead)
                boneMappingObj.addProperty("body", cache.boneMappingBody)
                boneMappingObj.addProperty("left_arm", cache.boneMappingLeftArm)
                boneMappingObj.addProperty("right_arm", cache.boneMappingRightArm)
                boneMappingObj.addProperty("left_leg", cache.boneMappingLeftLeg)
                boneMappingObj.addProperty("right_leg", cache.boneMappingRightLeg)
                obj.add("bone-mapping", boneMappingObj)

                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("costumes", array)
            serializePacket(8, packet).also { cachedCostumesBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    fun sendCostumeAssignment(
        player: Player,
        assignments: Map<UUID, Map<String, String>>,
        removals: List<Pair<UUID, String?>> = emptyList()
    ) {
        val packet = JsonObject()
        val assignArray = JsonArray()
        assignments.forEach { (uuid, slotMap) ->
            val obj = JsonObject()
            obj.addProperty("uuid", uuid.toString())
            val costumesArray = JsonArray()
            slotMap.forEach { (slot, costumeId) ->
                val slotEntry = JsonObject()
                slotEntry.addProperty("slot", slot)
                slotEntry.addProperty("costume", costumeId)
                costumesArray.add(slotEntry)
            }
            obj.add("costumes", costumesArray)
            assignArray.add(obj)
        }
        packet.add("assignments", assignArray)

        val removeArray = JsonArray()
        removals.forEach { (uuid, slot) ->
            val removalObj = JsonObject()
            removalObj.addProperty("uuid", uuid.toString())
            if (slot != null) removalObj.addProperty("slot", slot)
            removeArray.add(removalObj)
        }
        packet.add("removals", removeArray)

        sendPacket(player, 9, packet)
    }

    fun sendBlockModels(player: Player, blockModels: Map<String, BlockModelCache>) {
        val bytes = cachedBlockModelsBytes ?: run {
            val array = JsonArray()
            blockModels.forEach { (id, cache) ->
                val obj = JsonObject()
                obj.addProperty("id", id)
                obj.addProperty("custom_variation", cache.customVariation)
                if (cache.customModelData > 0) {
                    obj.addProperty("custom_model_data", cache.customModelData)
                }
                // GeckoLib 模型资源
                obj.addProperty("geo", cache.geo)
                obj.addProperty("texture", cache.texture)
                obj.addProperty("animation", cache.animation)
                obj.addProperty("scale", cache.scale)
                obj.addProperty("idle_animation", cache.idleAnimation)
                obj.addProperty("block_scale", cache.blockScale)
                obj.addProperty("item_offset_x", cache.itemOffsetX)
                obj.addProperty("item_offset_y", cache.itemOffsetY)
                obj.addProperty("item_offset_z", cache.itemOffsetZ)
                obj.addProperty("hardness", cache.hardness)
                if (cache.preferredTool.isNotBlank()) {
                    obj.addProperty("preferred_tool", cache.preferredTool)
                }
                if (cache.breakSound.isNotBlank()) {
                    obj.addProperty("break_sound", cache.breakSound)
                }
                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("blocks", array)
            serializePacket(10, packet).also { cachedBlockModelsBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }


    /**
     * Send per-player placeholder values for a single screen (Packet 13).
     * NOT cached — values differ per player.
     */
    fun sendPlaceholderUpdate(player: Player, screenId: String, values: Map<String, String>) {
        val packet = JsonObject()
        packet.addProperty("screen-id", screenId)
        val valuesObj = JsonObject()
        for ((key, value) in values) {
            valuesObj.addProperty(key, value)
        }
        packet.add("values", valuesObj)
        sendPacket(player, 13, packet)
    }

    fun sendReloadResourceManagerPacket(player: Player) {
        sendPacket(player, 99, JsonObject())
    }

    /**
     * 发送全量方块位置同步（packet ID 11）。
     * 在玩家加入时调用，将所有已追踪的方块位置发送给客户端。
     */
    fun sendBlockPositions(player: Player) {
        val world = player.world.name
        val positions = CustomBlockTracker.getAll(world)
        if (positions.isEmpty()) return

        val array = JsonArray()
        positions.forEach { (packed, variation) ->
            val obj = JsonObject()
            obj.addProperty("x", CustomBlockTracker.unpackX(packed))
            obj.addProperty("y", CustomBlockTracker.unpackY(packed))
            obj.addProperty("z", CustomBlockTracker.unpackZ(packed))
            obj.addProperty("v", variation)
            array.add(obj)
        }
        val packet = JsonObject()
        packet.add("positions", array)
        sendPacket(player, 11, packet)
    }

    /**
     * 发送增量方块位置更新（packet ID 12）。
     * 通知指定世界中的所有在线玩家。
     */
    fun sendBlockPositionUpdate(world: World, action: String, x: Int, y: Int, z: Int, variation: Int) {
        val packet = JsonObject()
        packet.addProperty("action", action)
        packet.addProperty("x", x)
        packet.addProperty("y", y)
        packet.addProperty("z", z)
        if (action == "add") {
            packet.addProperty("v", variation)
        }
        world.players.forEach { player ->
            sendPacket(player, 12, packet)
        }
    }

    private fun sendPacket(player: Player, internalPacketId: Int, obj: JsonObject) {
        sendSerializedPacket(player, serializePacket(internalPacketId, obj))
    }
}