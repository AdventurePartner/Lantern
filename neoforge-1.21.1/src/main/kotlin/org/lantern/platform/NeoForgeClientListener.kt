package org.lantern.platform

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import org.lantern.LanternNeoForge
import org.lantern.feature.FeatureHooks
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.SessionCleanup
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.storage.HudLayer
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.model.util.EntityStateUtil
import org.lantern.uix.canvas.impl.GuiCanvas
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.hud.VanillaHudElement
import org.lantern.uix.hud.VanillaHudPoses
import org.lantern.uix.hud.VanillaHudVisibility
import org.lantern.uix.input.FocusManager
import org.lantern.uix.renderer.OverlayRenderer
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

/**
 * NeoForge 1.21.1 的游戏总线事件。
 *
 * 相机翻滚不在这里处理：NeoForge 21.1 在 Camera.setup 内部、共享 CameraMixin 的 TAIL 之前
 * 就发出 ComputeCameraAngles，CameraMixin 写进四元数的翻滚即为最终结果（与 Fabric 相同）
 */
object NeoForgeClientListener {
    private val pressedKeys = mutableSetOf<String>()
    private val parsedKeys = mutableMapOf<String, ParsedKey>()
    private var wasMouseDown = false

    fun register() {
        val bus = NeoForge.EVENT_BUS
        bus.addListener(ClientTickEvent.Post::class.java, Consumer<ClientTickEvent.Post> { onClientTick() })
        bus.addListener(EntityLeaveLevelEvent::class.java, Consumer<EntityLeaveLevelEvent>(::onEntityLeaveLevel))
        bus.addListener(
            ClientPlayerNetworkEvent.LoggingOut::class.java,
            Consumer<ClientPlayerNetworkEvent.LoggingOut> { onDisconnect() }
        )
        bus.addListener(
            ScreenEvent.MouseButtonPressed.Pre::class.java,
            Consumer<ScreenEvent.MouseButtonPressed.Pre>(::onScreenMousePressed)
        )
        bus.addListener(RenderLevelStageEvent::class.java, Consumer<RenderLevelStageEvent>(WorldImageNeoForgeEvents::onRenderLevel))

        bus.addListener(RenderGuiLayerEvent.Pre::class.java, Consumer<RenderGuiLayerEvent.Pre>(::onRenderGuiLayer))
        // 原版元素摆放：NeoForge 没有 MONITOR，排在 LOWEST 之后的监听仍可能取消，
        // 推入后没等到 Post 的在下一图层 Pre（HIGHEST）与整帧 Post 补弹
        bus.addListener(
            EventPriority.HIGHEST,
            true,
            RenderGuiLayerEvent.Pre::class.java,
            Consumer<RenderGuiLayerEvent.Pre> { event -> VanillaHudPoses.popPending(event.guiGraphics) }
        )
        bus.addListener(
            EventPriority.LOWEST,
            false,
            RenderGuiLayerEvent.Pre::class.java,
            Consumer<RenderGuiLayerEvent.Pre>(::onPlaceGuiLayer)
        )
        // LOWEST：其他模组在 Post 里补画的内容也跟着平移
        bus.addListener(
            EventPriority.LOWEST,
            RenderGuiLayerEvent.Post::class.java,
            Consumer<RenderGuiLayerEvent.Post> { event ->
                placeableLayers[event.name]?.let { VanillaHudPoses.end(event.guiGraphics, it) }
            }
        )
        bus.addListener(
            RenderGuiEvent.Post::class.java,
            Consumer<RenderGuiEvent.Post> { event -> VanillaHudPoses.popPending(event.guiGraphics) }
        )
    }

    private fun onClientTick() {
        val client = Minecraft.getInstance()
        LanternNeoForge.initializeContext(client)
        TextureHandler.tick()
        FeatureHooks.onClientTick(client)

        if (client.screen is GuiCanvas) {
            wasMouseDown = false
            return
        }

        handleKeyboardInput(client)
        handleMouseInput(client)
    }

    private fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        if (event.level.isClientSide) {
            EntityStateUtil.cleanupEntityPosition(event.entity.id)
            SessionCleanup.onEntityLeave(event.entity)
        }
    }

    private fun onDisconnect() {
        NeoForgePacketNetwork.clear()
        SessionCleanup.onDisconnect()
        FeatureHooks.onDisconnect()
        pressedKeys.clear()
        parsedKeys.clear()
        wasMouseDown = false
    }

    private fun onScreenMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (OverlayRenderer.handleClick(event.screen, event.mouseX, event.mouseY, event.button)) {
            event.isCanceled = true
        }
    }

    // ==================== 原版 HUD 隐藏与摆放 ====================
    // 1.21.1 的经验条与坐骑跳跃条是两个独立图层，不像 1.21.6+ 共用上下文信息栏

    private val placeableLayers: Map<ResourceLocation, VanillaHudElement> = mapOf(
        VanillaGuiLayers.HOTBAR to VanillaHudElement.HOTBAR,
        VanillaGuiLayers.SELECTED_ITEM_NAME to VanillaHudElement.SELECTED_ITEM_NAME,
        VanillaGuiLayers.PLAYER_HEALTH to VanillaHudElement.HEALTH,
        VanillaGuiLayers.ARMOR_LEVEL to VanillaHudElement.ARMOR,
        VanillaGuiLayers.FOOD_LEVEL to VanillaHudElement.FOOD,
        VanillaGuiLayers.AIR_LEVEL to VanillaHudElement.AIR,
        VanillaGuiLayers.VEHICLE_HEALTH to VanillaHudElement.VEHICLE_HEALTH,
        VanillaGuiLayers.EXPERIENCE_BAR to VanillaHudElement.EXPERIENCE_BAR,
        VanillaGuiLayers.JUMP_METER to VanillaHudElement.JUMP_BAR,
        VanillaGuiLayers.EXPERIENCE_LEVEL to VanillaHudElement.EXPERIENCE_LEVEL,
        VanillaGuiLayers.CROSSHAIR to VanillaHudElement.CROSSHAIR,
        VanillaGuiLayers.EFFECTS to VanillaHudElement.EFFECTS
    )

    private fun onRenderGuiLayer(event: RenderGuiLayerEvent.Pre) {
        val element = placeableLayers[event.name] ?: return
        if (!VanillaHudVisibility.isHidden(element)) return
        // 旁观模式的 HOTBAR 图层画的是旁观菜单，保持原版
        if (element == VanillaHudElement.HOTBAR && Minecraft.getInstance().player?.isSpectator == true) return
        event.isCanceled = true
    }

    private fun onPlaceGuiLayer(event: RenderGuiLayerEvent.Pre) {
        val element = placeableLayers[event.name] ?: return
        val client = Minecraft.getInstance()
        // 旁观模式下 HOTBAR / SELECTED_ITEM_NAME 画的是旁观菜单与提示，不移动
        if (client.player?.isSpectator == true &&
            (element == VanillaHudElement.HOTBAR || element == VanillaHudElement.SELECTED_ITEM_NAME)
        ) return
        val gui = client.gui
        val graphics = event.guiGraphics
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        // 左右两列从底部向上堆叠：元素在本图层 Pre 时读取当前 leftHeight / rightHeight 定位
        val x: Int
        val y: Int
        when (element) {
            VanillaHudElement.HOTBAR -> { x = width / 2 - 91; y = height - 22 }
            VanillaHudElement.SELECTED_ITEM_NAME -> {
                x = width / 2
                y = height - maxOf(gui.leftHeight, gui.rightHeight, 59) +
                    if (client.gameMode?.canHurtPlayer() == false) 14 else 0
            }
            VanillaHudElement.HEALTH, VanillaHudElement.ARMOR -> { x = width / 2 - 91; y = height - gui.leftHeight }
            VanillaHudElement.FOOD, VanillaHudElement.AIR, VanillaHudElement.VEHICLE_HEALTH -> {
                x = width / 2 + 91
                y = height - gui.rightHeight
            }
            VanillaHudElement.EXPERIENCE_BAR, VanillaHudElement.JUMP_BAR -> { x = width / 2 - 91; y = height - 29 }
            VanillaHudElement.EXPERIENCE_LEVEL -> { x = width / 2; y = height - 35 }
            VanillaHudElement.CROSSHAIR -> { x = (width - 15) / 2; y = (height - 15) / 2 }
            VanillaHudElement.EFFECTS -> { x = width; y = 0 }
        }
        VanillaHudPoses.begin(graphics, element, x, y)
    }

    // ==================== 按键与顶层 HUD 点击 ====================

    private fun handleMouseInput(client: Minecraft) {
        val window = client.window.window
        val scale = client.window.guiScale
        val mx = (client.mouseHandler.xpos() / scale).toInt()
        val my = (client.mouseHandler.ypos() / scale).toInt()

        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        // 顶层 HUD 在任何界面打开时都不绘制（HudRenderer.renderTop），点击与悬停也不能穿透过去
        if (client.screen != null) {
            wasMouseDown = leftDown
            return
        }
        if (leftDown && !wasMouseDown) {
            FocusManager.blur()
            UiScreenStorage.hudRoots(HudLayer.TOP).forEach { root ->
                EventDispatcher.dispatchClick(root, mx, my)
            }
        }
        wasMouseDown = leftDown

        UiScreenStorage.hudRoots(HudLayer.TOP).forEach { root ->
            EventDispatcher.updateHover(root, mx, my)
        }
    }

    private fun handleKeyboardInput(client: Minecraft) {
        val window = client.window.window
        val keyboards = ResourceHandler.getKeyboards()
        val inGui = client.screen != null

        keyboards.forEach { (spec, wrapper) ->
            val key = parsedKeys.computeIfAbsent(spec) { parseKeySpec(it) }
            val isDown = isDown(window, key)
            val wasDown = pressedKeys.contains(spec)

            if (isDown == wasDown) {
                return@forEach
            }

            if (isDown) {
                pressedKeys.add(spec)
                if (wrapper.press) {
                    NeoForgePacketNetwork.sendKeyboardPacket(spec, true, inGui)
                }
            } else {
                pressedKeys.remove(spec)
                if (!wrapper.press) {
                    NeoForgePacketNetwork.sendKeyboardPacket(spec, false, inGui)
                }
            }
        }
    }

    private fun isDown(window: Long, key: ParsedKey): Boolean {
        if (key.keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_CONTROL != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_SHIFT != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_ALT != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) return false
        }
        if (key.requiredMods and GLFW.GLFW_MOD_SUPER != 0) {
            if (!isAnyDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)) return false
        }
        return GLFW.glfwGetKey(window, key.keyCode) == GLFW.GLFW_PRESS
    }

    private fun isAnyDown(window: Long, left: Int, right: Int): Boolean {
        return GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS
    }

    private fun parseKeySpec(spec: String): ParsedKey {
        val parts = spec.lowercase().split("+").map { it.trim() }.filter { it.isNotEmpty() }
        var mods = 0
        var main = ""

        for (part in parts) {
            when (part) {
                "ctrl", "control" -> mods = mods or GLFW.GLFW_MOD_CONTROL
                "shift" -> mods = mods or GLFW.GLFW_MOD_SHIFT
                "alt" -> mods = mods or GLFW.GLFW_MOD_ALT
                "super", "win", "meta", "cmd" -> mods = mods or GLFW.GLFW_MOD_SUPER
                else -> main = part
            }
        }

        // 只写了修饰键、没写主键时（如 "alt"、"ctrl"），把它本身当主键：
        // 否则 main 留空、keyCode 落到 UNKNOWN，isDown 首行即返回 false，
        // 这类配置永远不会触发。带主键的组合（"alt+q"）不受影响
        if (main.isEmpty() && parts.size == 1) {
            main = parts[0]
            mods = 0
        }
        val keyCode = when {
            main.length == 1 && main[0] in 'a'..'z' -> GLFW.GLFW_KEY_A + (main[0] - 'a')
            main.length == 1 && main[0] in '0'..'9' -> GLFW.GLFW_KEY_0 + (main[0] - '0')
            main.startsWith("f") && main.drop(1).toIntOrNull() in 1..25 -> {
                GLFW.GLFW_KEY_F1 + (main.drop(1).toInt() - 1)
            }
            main == "space" -> GLFW.GLFW_KEY_SPACE
            main == "enter" -> GLFW.GLFW_KEY_ENTER
            main == "tab" -> GLFW.GLFW_KEY_TAB
            main == "escape" || main == "esc" -> GLFW.GLFW_KEY_ESCAPE
            main == "backspace" -> GLFW.GLFW_KEY_BACKSPACE
            main == "delete" -> GLFW.GLFW_KEY_DELETE
            main == "insert" -> GLFW.GLFW_KEY_INSERT
            main == "home" -> GLFW.GLFW_KEY_HOME
            main == "end" -> GLFW.GLFW_KEY_END
            main == "pageup" -> GLFW.GLFW_KEY_PAGE_UP
            main == "pagedown" -> GLFW.GLFW_KEY_PAGE_DOWN
            main == "up" -> GLFW.GLFW_KEY_UP
            main == "down" -> GLFW.GLFW_KEY_DOWN
            main == "left" -> GLFW.GLFW_KEY_LEFT
            main == "right" -> GLFW.GLFW_KEY_RIGHT
            main == "ctrl" || main == "control" || main == "left_ctrl" || main == "lctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL
            main == "right_ctrl" || main == "rctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL
            main == "shift" || main == "left_shift" || main == "lshift" -> GLFW.GLFW_KEY_LEFT_SHIFT
            main == "right_shift" || main == "rshift" -> GLFW.GLFW_KEY_RIGHT_SHIFT
            main == "alt" || main == "left_alt" || main == "lalt" -> GLFW.GLFW_KEY_LEFT_ALT
            main == "right_alt" || main == "ralt" -> GLFW.GLFW_KEY_RIGHT_ALT
            main == "super" || main == "win" || main == "meta" || main == "cmd" -> GLFW.GLFW_KEY_LEFT_SUPER
            else -> GLFW.GLFW_KEY_UNKNOWN
        }

        return ParsedKey(keyCode, mods)
    }
}

private data class ParsedKey(val keyCode: Int, val requiredMods: Int)
