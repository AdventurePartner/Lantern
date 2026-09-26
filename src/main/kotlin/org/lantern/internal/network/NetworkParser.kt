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
import org.lantern.core.bone.BoneMapping
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.core.anim.statemap.AnimationStateMapping
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import java.util.UUID

object NetworkParser {

    /**
     * packetId 15（动画播控）的处理器，由支持运行时动画控制的客户端平台注册。
     * action: play | stop | pause | resume | seek；
     * seek 时 seekSeconds 为跳转目标（秒），其他 action 为 -1。
     * toCombatLayer 由 layer 字段决定：combat = 上身出招层（腿保持移动状态），
     * 缺省 motion = 全身运动层（压住行走）。
     * 目标可以是实体（entityModels 条目）或玩家（host-driven 外观）。
     * library：可选，剪辑所在动画库（file 字段）；null = 用目标当前绑定的库。
     * seq：服务端实例序号（-1 = 无），finish 回带用于按实例匹配。
     * exitTicks：播完清层的退出过渡 tick（-1 = 沿用起手过渡）。
     * 未注册的平台忽略该包。
     */
    var animationControlHandler: ((uuid: UUID, action: String, animation: String, transition: Int, loop: Boolean, speed: Float, seekSeconds: Float, uninterruptible: Boolean, toCombatLayer: Boolean, library: String?, seq: Long, exitTicks: Int) -> Unit)? =
        null

    /**
     * C2S 动画生命周期事件上报（如 once 播完），由各平台网络层注册发送实现。
     * 事件类型: "finish"
     */
    var animationEventSender: ((uuid: UUID, animation: String, event: String) -> Unit)? = null

    /**
     * packetId 17（molang 变量同步）的处理器，由实现了表达式动画求值的客户端平台注册。
     * 参数: (实体UUID, 变量名 -> 值字符串)；值可为数字或表达式，空串 = 删除该变量。
     * 未注册的平台忽略该包。
     */
    var molangVariableHandler: ((uuid: UUID, vars: Map<String, String>) -> Unit)? = null

    /**
     * packetId 18 相机演出指令（lock/unlock/shake/fov/offset/clear）的处理器，
     * 由实现了相机控制的客户端平台注册。
     * "shoulder"（越肩参数）由共享 [org.lantern.camera.ShoulderCameraState] 直写，不经此 handler。
     */
    var cameraActionHandler: ((action: String, obj: JsonObject) -> Unit)? = null

    /**
     * packetId 19 玩家主动动作定义（按键触发的翻滚等），由支持本地动作触发的平台注册。
     * 载荷整体交给平台侧解析——动作定义引用的剪辑与层语义是平台动画内核的概念。
     *
     * 载荷形如 { "actions": [ ... ] }，每项两种形态：
     *   方向动作 { id, key, file, directions:{forward:.., left:..}, transition,
     *              exit-transition, cooldown, layer, exclusive, uninterruptible,
     *              invulnerable, suppress-vanilla-attack, distance, dash-duration,
     *              vertical, airborne, require-ground }
     *   连招     { id, trigger:"attack", costume, file, layer,
     *              steps:[{animation, cancel-at, window, transition, exit-transition}] }
     * 触发与播放全在客户端本地：出招延迟对动作玩法敏感，服务端只下发定义。
     */
    var playerActionHandler: ((obj: JsonObject) -> Unit)? = null

    /**
     * packetId 20 载体绑定（bind / unbind），由实现了渲染重定向的客户端平台注册。
     *
     * 绑定态 { follower, host, offset:[x,y,z], rotate, visible, durationMs }；
     * 解绑只带 { follower }（无 host 字段即解绑）。
     * 语义纯客户端渲染层：载体的服务端坐标、碰撞、移动逻辑一概不动，
     * 只是渲染时把它画到宿主的插值位置上。
     */
    var entityBindHandler: ((obj: JsonObject) -> Unit)? = null

    /**
     * packetId 21 玩家输入锁，由实现了输入压制的客户端平台注册。
     *
     * 加锁 { id, locks:["move","jump",...], durationMs }；
     * 解锁 { clear:true, id? }（省略 id = 清除该玩家全部锁）。
     * 多来源按 id 分别记账、并集生效，各自到期。
     */
    var inputLockHandler: ((obj: JsonObject) -> Unit)? = null

    /**
     * 分发表。未匹配的 packetId 自然落空（when 语句无 else 分支）——
     * 老客户端收到新增包号即为静默跳过，wire 兼容靠这一点维持，勿改成穷举分支
     */
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
            15 -> parseAnimationControl(obj)
            17 -> parseMolangVariables(obj)
            18 -> parseCameraControl(obj)
            19 -> playerActionHandler?.invoke(obj)
            20 -> entityBindHandler?.invoke(obj)
            21 -> inputLockHandler?.invoke(obj)
            99 -> reloadResourcePack()
        }
    }

    /**
     * packetId 18（相机控制）。
     * 阶段一仅 "shoulder"（越肩参数，直写共享 [org.lantern.camera.ShoulderCameraState]）；
     * 后续演出指令（lock/shake/fov/...）在同一包号下扩展 action 分支。
     */
    private fun parseCameraControl(obj: JsonObject) {
        val action = obj.get("action")?.asString ?: return
        // NeoForge 收包在网络线程（Fabric 恰好已在主线程）——统一调度到主线程再写渲染状态，
        // 避免 CameraControl/ShoulderCameraState 与渲染线程的数据竞争与元组撕裂
        Minecraft.getInstance().execute {
            when (action) {
                "shoulder" -> org.lantern.camera.ShoulderCameraState.update(
                    obj.get("enabled")?.asBoolean ?: false,
                    obj.get("offset-x")?.asDouble ?: 0.0,
                    obj.get("offset-y")?.asDouble ?: 0.0,
                    obj.get("distance")?.asDouble ?: 4.0
                )
                "lock", "unlock", "shake", "fov", "offset", "path", "watch", "clear" ->
                    cameraActionHandler?.invoke(action, obj)
            }
        }
    }

    private fun parseMolangVariables(obj: JsonObject) {
        val handler = molangVariableHandler ?: return
        val uuid = obj.get("uuid")?.asString
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        val varsObj = obj.getAsJsonObject("vars") ?: return
        val vars = LinkedHashMap<String, String>()
        for ((key, value) in varsObj.entrySet()) {
            if (value.isJsonPrimitive) vars[key] = value.asString
        }
        if (vars.isNotEmpty()) handler(uuid, vars)
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
                // 上身骨骼集随外观下发：换外观即换骨架，遮罩要跟着走
                val upperBones = animationsObj.getAsJsonArray("upper-body-bones")
                animationStates = AnimationStateMapping.fromStatesJson(statesObj, upperBones)
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
            val hostDriven = it.get("host-driven")?.asBoolean ?: false
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
                boneMapping = boneMapping,
                hostDriven = hostDriven
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

    private fun parseAnimationControl(obj: JsonObject) {
        val handler = animationControlHandler ?: return
        val uuid = obj.get("uuid")?.asString
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        val action = obj.get("action")?.asString ?: return
        val animation = obj.get("animation")?.asString ?: return
        val transition = obj.get("transition")?.asInt ?: 0
        val loop = obj.get("mode")?.asString != "once"
        val speed = obj.get("speed")?.asFloat ?: 1.0f
        val seekSeconds = obj.get("time")?.asFloat ?: -1f
        val uninterruptible = obj.get("uninterruptible")?.asBoolean ?: false
        // 归层：combat = 上身出招层（腿继续走），缺省 motion = 全身
        val toCombatLayer = obj.get("layer")?.asString.equals("combat", ignoreCase = true)
        // 指令自带动画库：技能剪辑不再绑死在目标当前的外观库上。库 id 以字符串
        // 原样下传，命名空间的补全由消费侧的资源定位完成（common-core 不碰 ResourceLocation）
        val library = obj.get("file")?.asString?.takeIf { it.isNotBlank() }
        val seq = obj.get("seq")?.asLong ?: -1L
        val exitTicks = obj.get("exit")?.asInt ?: -1
        if (action == "seek" && seekSeconds < 0f) return
        handler(uuid, action, animation, transition, loop, speed, seekSeconds, uninterruptible, toCombatLayer, library, seq, exitTicks)
    }

    private fun reloadResourcePack() {
        Minecraft.getInstance().execute { Minecraft.getInstance().reloadResourcePacks(); }
    }
}
