package org.lantern.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.cache.ItemIconCache
import org.lantern.cache.KeyCache
import org.lantern.config.Configurations
import org.lantern.config.UiConfigurations
import org.lantern.handler.CacheHandler
import org.lantern.util.JsonUtil
import org.lantern.util.TextUtil.colorify
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException


object NetworkHandler {

    fun sendPackets(player: Player) {
        sendCharactersPacket(player, Configurations.characters)
        sendEntityModelsPacket(player, Configurations.models)
        sendKeyboards(player, CacheHandler.keys)
        sendItemIcons(player, CacheHandler.itemIcons)
        sendUiScreens(player, UiConfigurations.getScreens())
        sendResourcePackKey(player)

        // 延迟一秒发送重载资源数据包
        val relaodRunnable = Runnable { sendReloadResourceManagerPacket(player) }
        Bukkit.getScheduler().runTaskLaterAsynchronously(LanternPlugin.instance, relaodRunnable, 20L)
    }

    fun sendUiScreens(player: Player, screens: List<JsonObject>) {
        val array = JsonArray()
        screens.forEach { array.add(it) }
        val packet = JsonObject()
        packet.add("screens", array)
        sendPacket(player, 5, packet)
    }

    fun sendOpenGui(player: Player, screenId: String) {
        val packet = JsonObject()
        packet.addProperty("screen-id", screenId)
        sendPacket(player, 6, packet)
    }

    fun sendCharactersPacket(player: Player, config: FileConfiguration) {
        val array = JsonArray()
        config.getKeys(false).forEach {
            val section = config.getConfigurationSection(it) ?: return@forEach
            val obj = JsonObject()
            obj.addProperty("character", section.getString("character"))
            obj.addProperty("texture", section.getString("texture"))
            obj.addProperty("width", section.getInt("width"))
            obj.addProperty("height", section.getInt("height"))
            obj.addProperty("wide", section.getInt("wide"))
            array.add(obj)
        }
        val packet = JsonObject()
        packet.add("characters", array)
        sendPacket(player, 1, packet)
    }

    fun sendEntityModelsPacket(player: Player, config: FileConfiguration) {
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
        sendPacket(player, 2, packet)
    }

    fun sendKeyboards(player: Player, keys: Map<String, KeyCache>) {
        val array = JsonArray()
        keys.forEach { (k, v) ->
            val obj = JsonObject()
            obj.addProperty("key", k)
            obj.addProperty("press", v.press)
            array.add(obj)
        }
        val packet = JsonObject()
        packet.add("keys", array)
        sendPacket(player, 3, packet)
    }

    fun sendItemIcons(player: Player, itemIcons: Map<Int, ItemIconCache>) {
        val array = JsonArray()
        itemIcons.forEach { (k, v) ->
            val obj = JsonObject()
            obj.addProperty("data", k)
            obj.addProperty("identifier", v.identifier)
            obj.addProperty("texture", v.texture)
            array.add(obj)
        }
        val packet = JsonObject()
        packet.add("icons", array)
        sendPacket(player, 4, packet)
    }

    fun sendResourcePackKey(player: Player) {
        val key = LanternPlugin.instance.config.getString("resource-pack-key") ?: return
        if (key.isBlank()) return
        val packet = JsonObject()
        packet.addProperty("key", key)
        sendPacket(player, 7, packet)
    }

    fun sendReloadResourceManagerPacket(player: Player) {
        sendPacket(player, 99, JsonObject())
    }

    private fun sendPacket(player: Player, internalPacketId: Int, obj: JsonObject) {
        try {
            ByteArrayOutputStream().use { byteStream ->
                DataOutputStream(byteStream).use { dataStream ->
                    val str = JsonUtil.toJson(obj)
                    dataStream.writeByte(0)
                    dataStream.writeInt(internalPacketId)
                    dataStream.write(str.toByteArray(Charsets.UTF_8))
                    player.sendPluginMessage(
                        LanternPlugin.instance,
                        "lantern:main",
                        byteStream.toByteArray()
                    )
                }
            }
        } catch (e: IOException) {
            LanternPlugin.instance.logger.warning("Failed to send message to Forge client: " + e.message)
        }
    }
}