package org.lantern.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Entity
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
    private var cachedChatChannelsBytes: ByteArray? = null
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
        cachedChatChannelsBytes = null
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
        org.lantern.camera.ShoulderCameraService.pushState(player)
        sendChatChannels(player, readChatChannels(LanternPlugin.instance.config))
        sendItemIcons(player, CacheHandler.itemIcons)
        sendUiScreens(player, UiConfigurations.getScreens())
        sendCostumesPacket(player, CacheHandler.costumes)
        sendCostumeAssignment(player, CostumeAssignmentHandler.getAll())
        sendBlockModels(player, CacheHandler.blockModels)
        sendBlockPositions(player)

        // 延迟一秒发送重载资源数据包
        val reloadRunnable = Runnable { sendReloadResourceManagerPacket(player) }
        Bukkit.getScheduler().runTaskLater(LanternPlugin.instance, reloadRunnable, 20L)
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

                        // 解析 states：任意状态 key，值支持动画名简写或 {animation,mode,transition} 展开
                        if (animationsSection.contains("states")) {
                            val statesSection = animationsSection.getConfigurationSection("states")
                            if (statesSection != null) {
                                animationsObj.add("states", buildStatesJson(statesSection))
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
            // 相机微调键并入客户端轮询集（客户端只轮询 packet 3 下发的键）；
            // 与 keys.yml 冲突的 spec 保留原绑定，服务端处理时相机优先
            org.lantern.camera.ShoulderCameraService.pollKeySpecs().forEach { spec ->
                if (keys.containsKey(spec)) return@forEach
                val obj = JsonObject()
                obj.addProperty("key", spec)
                obj.addProperty("press", true)
                array.add(obj)
            }
            val packet = JsonObject()
            packet.add("keys", array)
            serializePacket(3, packet).also { cachedKeysBytes = it }
        }
        sendSerializedPacket(player, bytes)
    }

    /**
     * 越肩相机状态推送（packet 18 action=shoulder，per-player 不缓存）。
     * 进服同步、/lantern reload、按键微调与 /lantern cam 命令后调用。
     */
    fun sendCameraState(player: Player, enabled: Boolean, offsetX: Double, offsetY: Double, distance: Double) {
        val packet = JsonObject()
        packet.addProperty("action", "shoulder")
        packet.addProperty("enabled", enabled)
        packet.addProperty("offset-x", offsetX)
        packet.addProperty("offset-y", offsetY)
        packet.addProperty("distance", distance)
        sendPacket(player, 18, packet)
    }

    // ============ 相机演出指令（packet 18，阶段二） ============

    /** 强制玩家视角看向世界坐标（渲染层平滑转向；sync=true 同时写回真实朝向）。duration<=0 永久直到 unlock。 */
    fun cameraLock(
        player: Player,
        x: Double, y: Double, z: Double,
        smooth: Double = 0.3,
        duration: Double = 0.0,
        sync: Boolean = false
    ) {
        val packet = JsonObject()
        packet.addProperty("action", "lock")
        val target = JsonObject()
        target.addProperty("x", x)
        target.addProperty("y", y)
        target.addProperty("z", z)
        packet.add("target", target)
        packet.addProperty("smooth", smooth.coerceAtLeast(0.0))
        packet.addProperty("duration", duration.coerceAtLeast(0.0))
        packet.addProperty("sync", sync)
        sendPacket(player, 18, packet)
    }

    /** 强制玩家视角跟随实体（每帧取实体眼睛位置）。 */
    fun cameraLockEntity(
        player: Player,
        entityUuid: UUID,
        smooth: Double = 0.3,
        duration: Double = 0.0,
        sync: Boolean = false
    ) {
        val packet = JsonObject()
        packet.addProperty("action", "lock")
        packet.addProperty("entity", entityUuid.toString())
        packet.addProperty("smooth", smooth.coerceAtLeast(0.0))
        packet.addProperty("duration", duration.coerceAtLeast(0.0))
        packet.addProperty("sync", sync)
        sendPacket(player, 18, packet)
    }

    /** 解除 lock（无痕恢复玩家自身朝向）。 */
    fun cameraUnlock(player: Player) {
        sendPacket(player, 18, JsonObject().also { it.addProperty("action", "unlock") })
    }

    /** 相机震动：正弦 × 随机相位，默认线性衰减；幅度单位=格。 */
    fun cameraShake(
        player: Player,
        amplitude: Double = 0.3,
        frequency: Double = 8.0,
        duration: Double = 0.5,
        decay: Boolean = true
    ) {
        val packet = JsonObject()
        packet.addProperty("action", "shake")
        packet.addProperty("amplitude", amplitude.coerceIn(0.0, 0.5))
        packet.addProperty("frequency", frequency.coerceIn(0.1, 30.0))
        packet.addProperty("duration", duration.coerceAtLeast(0.05))
        packet.addProperty("decay", decay)
        sendPacket(player, 18, packet)
    }

    /** 临时 FOV（度）；value=null 表示恢复玩家设置值。 */
    fun cameraFov(player: Player, fov: Double?, transition: Double = 1.0) {
        val packet = JsonObject()
        packet.addProperty("action", "fov")
        fov?.let { packet.addProperty("value", it.coerceIn(1.0, 170.0)) }
        packet.addProperty("transition", transition.coerceAtLeast(0.0))
        sendPacket(player, 18, packet)
    }

    /** 朝向偏移叠加（pitch/yaw/roll，度）。 */
    fun cameraOffset(
        player: Player,
        pitch: Double = 0.0,
        yaw: Double = 0.0,
        roll: Double = 0.0,
        transition: Double = 2.0
    ) {
        val packet = JsonObject()
        packet.addProperty("action", "offset")
        packet.addProperty("pitch", pitch)
        packet.addProperty("yaw", yaw)
        packet.addProperty("roll", roll)
        packet.addProperty("transition", transition.coerceAtLeast(0.0))
        sendPacket(player, 18, packet)
    }

    /** 演出状态清空（lock/shake/fov/offset；不影响越肩模式）。 */
    fun cameraClear(player: Player) {
        sendPacket(player, 18, JsonObject().also { it.addProperty("action", "clear") })
    }

    /** 对以 origin 为中心 radius 半径内的同世界玩家逐一下发（阶段四动作轨道复用）。 */
    fun cameraControlRadius(origin: org.bukkit.Location, radius: Double, applier: (Player) -> Unit) {
        val radiusSq = radius * radius
        origin.world?.players?.forEach { player ->
            if (player.location.distanceSquared(origin) <= radiusSq) {
                applier(player)
            }
        }
    }

    private data class ChatChannelConfig(
        val id: String,
        val displayName: String,
        val prefixes: List<String>,
        val filter: List<String>
    )

    private fun readChatChannels(config: FileConfiguration): List<ChatChannelConfig> {
        val channels = ArrayList<ChatChannelConfig>()
        val seenIds = HashSet<String>()

        config.getMapList("chat-channels").forEach { section ->
            val id = section["id"]?.toString()?.trim().orEmpty()
            if (id.isEmpty() || !seenIds.add(id)) {
                return@forEach
            }

            val displayName = section["display-name"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: id
            val prefixes = (section["prefixes"] as? Iterable<*>)
                ?.asSequence()
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.toList()
                ?: emptyList()
            val filter = (section["filter"] as? Iterable<*>)
                ?.asSequence()
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.toList()
                ?: emptyList()


            channels.add(ChatChannelConfig(id, displayName, prefixes, filter))
        }

        return channels
    }

    private fun sendChatChannels(player: Player, channels: List<ChatChannelConfig>) {
        val bytes = cachedChatChannelsBytes ?: run {
            val array = JsonArray()
            channels.forEach { channel ->
                val obj = JsonObject()
                obj.addProperty("id", channel.id)
                obj.addProperty("display-name", channel.displayName)
                val prefixes = JsonArray()
                channel.prefixes.forEach { prefixes.add(it) }
                obj.add("prefixes", prefixes)
                val filter = JsonArray()
                channel.filter.forEach { filter.add(it) }
                obj.add("filter", filter)
                array.add(obj)
            }

            val packet = JsonObject()
            packet.add("channels", array)
            serializePacket(14, packet).also { cachedChatChannelsBytes = it }
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

    /**
     * 向所有在线玩家广播当前全量装扮分配。命令/GUI 改动装扮后调用以即时同步。
     */
    fun broadcastCostumeAssignment() {
        val all = CostumeAssignmentHandler.getAll()
        Bukkit.getOnlinePlayers().forEach { sendCostumeAssignment(it, all) }
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

    /**
     * 发送动画播控包（packet ID 15）。
     * 客户端对该实体播放/停止指定动画；非 Lantern 模型实体的包会被客户端忽略。
     */
    fun sendAnimationControl(
        player: Player,
        entityUuid: UUID,
        action: String,
        animation: String,
        transitionTicks: Int,
        loop: Boolean,
        speed: Float,
        timeSeconds: Float? = null
    ) {
        val packet = JsonObject()
        packet.addProperty("uuid", entityUuid.toString())
        packet.addProperty("action", action)
        packet.addProperty("animation", animation)
        packet.addProperty("transition", transitionTicks.coerceAtLeast(0))
        packet.addProperty("mode", if (loop) "loop" else "once")
        packet.addProperty("speed", speed)
        timeSeconds?.let { packet.addProperty("time", it) }
        sendPacket(player, 15, packet)
    }

    /**
     * 对实体播放指定动画（loop 循环直到 stop；once 播完自动回落并上报 finish）。
     * 广播给全体在线玩家，同时驱动服务端编排（动作轨道/事件/链式）。
     */
    fun playAnimation(
        entity: Entity,
        animation: String,
        transitionTicks: Int = 5,
        loop: Boolean = true,
        speed: Float = 1.0f
    ) {
        val safeSpeed = if (speed > 0.01f) speed else 1.0f
        // 濒死(假死亡)实体只允许 death 动画：MM ~onTimer 技能不受 setAI(false) 影响，
        // 濒死期间的踩踏等 lanternanim 指令会顶掉正在播放的 die，导致死亡流程断裂
        if (org.lantern.animation.DeathAnimationInterceptor.isDying(entity.uniqueId) &&
            animation != org.lantern.animation.AnimationOrchestrator.deathAnimationOf(entity)
        ) {
            return
        }
        Bukkit.getOnlinePlayers().forEach {
            sendAnimationControl(it, entity.uniqueId, "play", animation, transitionTicks, loop, safeSpeed)
        }
        org.lantern.animation.AnimationOrchestrator.onPlay(entity, animation, loop)
    }

    /** 停止实体上由 [playAnimation] 播放的指定动画。 */
    fun stopAnimation(entity: Entity, animation: String, transitionTicks: Int = 0) {
        Bukkit.getOnlinePlayers().forEach {
            sendAnimationControl(it, entity.uniqueId, "stop", animation, transitionTicks, false, 1.0f)
        }
        org.lantern.animation.AnimationOrchestrator.onStop(entity, animation)
    }

    /** 暂停实体当前播控动画（冻结时间轴与 once 到期，直到 resume/stop）。 */
    fun pauseAnimation(entity: Entity, animation: String) {
        Bukkit.getOnlinePlayers().forEach {
            sendAnimationControl(it, entity.uniqueId, "pause", animation, 0, false, 1.0f)
        }
    }

    /** 恢复实体被 [pauseAnimation] 暂停的播控动画。 */
    fun resumeAnimation(entity: Entity, animation: String) {
        Bukkit.getOnlinePlayers().forEach {
            sendAnimationControl(it, entity.uniqueId, "resume", animation, 0, false, 1.0f)
        }
    }

    /** 跳转实体当前播控动画的时间轴到指定秒（loop 取模，once 钳制到长度内）。 */
    fun seekAnimation(entity: Entity, animation: String, seconds: Float) {
        Bukkit.getOnlinePlayers().forEach {
            sendAnimationControl(it, entity.uniqueId, "seek", animation, 0, false, 1.0f, seconds)
        }
    }

    /**
     * 同步实体的 molang 变量（packet 17，S2C 广播）。
     * 值为字符串：数字或客户端求值的 molang 表达式（可引用 query.*）；空串 = 删除该变量。
     * 更新为合并语义，只传需要变更的键值对。
     */
    fun setMolangVariables(entity: Entity, vars: Map<String, String>) {
        val packet = JsonObject()
        packet.addProperty("uuid", entity.uniqueId.toString())
        val varsObj = JsonObject()
        vars.forEach { (key, value) -> varsObj.addProperty(key, value) }
        packet.add("vars", varsObj)
        val bytes = serializePacket(17, packet)
        Bukkit.getOnlinePlayers().forEach { sendSerializedPacket(it, bytes) }
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

    /** entityModels 旧五状态 key：string 写法保持 string 下发（线上旧客户端按 asString 读） */
    private val legacyStateKeys = setOf("idle", "walk", "attack", "hurt", "death")

    /**
     * 状态默认语义表（未显式配置 mode/transition 时服务端展开，客户端不做二次默认）：
     * 一次性动作起手要快（2-3 tick），蓄力/空中过渡稍缓，持续姿态默认 5 tick。
     * transition 默认值与事件驱动播放共用 AnimationOrchestrator.defaultTransitionOf（单一事实源）
     */
    private val stateDefaultModes = mapOf(
        "jump" to "once", "landing" to "once", "spawn" to "once",
        "heal" to "once", "attack" to "once", "pull_bow" to "hold"
    )

    private fun defaultModeOf(key: String): String {
        stateDefaultModes[key]?.let { return it }
        if (key.startsWith("hold_")) return "hold"
        return "loop"
    }

    /**
     * states 配置节 -> 下发 JSON。值两种写法：
     * 简写 `sprint: run`（按默认语义表展开为完整 object）
     * 展开 `jump: {animation: jump, mode: once, transition: 3}`
     * 例外：旧五 key 的简写保持 string 下发，协议对旧客户端不变
     */
    private fun buildStatesJson(statesSection: org.bukkit.configuration.ConfigurationSection): JsonObject {
        val statesObj = JsonObject()
        for (key in statesSection.getKeys(false)) {
            val raw = statesSection.get(key) ?: continue
            when {
                raw is String -> {
                    if (raw.isBlank()) continue
                    if (key in legacyStateKeys) {
                        statesObj.addProperty(key, raw)
                    } else {
                        statesObj.add(key, expandState(key, raw, null))
                    }
                }
                raw is org.bukkit.configuration.ConfigurationSection -> {
                    val animation = raw.getString("animation")?.takeIf { it.isNotBlank() } ?: continue
                    statesObj.add(key, expandState(key, animation, raw))
                }
            }
        }
        return statesObj
    }

    private fun expandState(
        key: String,
        animation: String,
        section: org.bukkit.configuration.ConfigurationSection?
    ): JsonObject {
        val obj = JsonObject()
        obj.addProperty("animation", animation)
        obj.addProperty("mode", section?.getString("mode")?.takeIf { it.isNotBlank() } ?: defaultModeOf(key))
        obj.addProperty(
            "transition",
            section?.getInt(
                "transition",
                org.lantern.animation.AnimationOrchestrator.defaultTransitionOf(key)
            ) ?: org.lantern.animation.AnimationOrchestrator.defaultTransitionOf(key)
        )
        return obj
    }
}
