package org.lantern.platform

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.client.gui.overlay.ForgeGui
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.lantern.LanternForge
import org.lantern.internal.handler.ResourceHandler
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

object ForgeClientListener {
    private val pressedKeys = mutableSetOf<String>()
    private val parsedKeys = mutableMapOf<String, ParsedKey>()
    private var wasMouseDown = false

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val client = Minecraft.getInstance()
        LanternForge.initializeContext(client)
        org.lantern.internal.handler.TextureHandler.tick()

        if (client.screen is GuiCanvas) {
            wasMouseDown = false
            return
        }

        handleKeyboardInput(client)
        handleMouseInput(client)
    }

    @SubscribeEvent
    fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        if (event.level.isClientSide) {
            EntityStateUtil.cleanupEntityPosition(event.entity.id)
        }
    }

    @SubscribeEvent
    fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        LanternForge.clearClientCaches()
        clearInputState()
    }

    private fun clearInputState() {
        pressedKeys.clear()
        parsedKeys.clear()
        wasMouseDown = false
    }

    @SubscribeEvent
    fun onScreenMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (OverlayRenderer.handleClick(event.screen, event.mouseX, event.mouseY, event.button)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onRenderGuiOverlay(event: RenderGuiOverlayEvent.Pre) {
        val element = vanillaOverlays[event.overlay.id()] ?: return
        if (!VanillaHudVisibility.isHidden(element)) return
        // 旁观模式的 HOTBAR overlay 画的是旁观菜单，保持原版
        if (element == VanillaHudElement.HOTBAR && Minecraft.getInstance().player?.isSpectator == true) return
        event.isCanceled = true
    }

    // 上一个 overlay 的摆放推入若没等到 Post（ForgeGui 对每个 overlay try/catch，抛异常会跳过 Post），先补弹
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    fun onOverlayPreCleanup(event: RenderGuiOverlayEvent.Pre) {
        VanillaHudPoses.popPending(event.guiGraphics)
    }

    // MONITOR 只收到最终未被取消的事件，且此阶段之后不允许再取消，随后必定有 Post
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onOverlayPrePlace(event: RenderGuiOverlayEvent.Pre) {
        val element = vanillaOverlays[event.overlay.id()] ?: return
        val client = Minecraft.getInstance()
        // 旁观模式下 HOTBAR / ITEM_NAME 画的是旁观菜单与提示，不移动
        if (client.player?.isSpectator == true &&
            (element == VanillaHudElement.HOTBAR || element == VanillaHudElement.SELECTED_ITEM_NAME)
        ) return
        val gui = client.gui as? ForgeGui ?: return
        val graphics = event.guiGraphics
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        // ForgeGui 的左右两列从底部向上堆叠：元素在 Pre 时读取当前 leftHeight / rightHeight 定位
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
            VanillaHudElement.JUMP_BAR -> { x = width / 2 - 91; y = height - 29 }
            VanillaHudElement.CROSSHAIR -> { x = (width - 15) / 2; y = (height - 15) / 2 }
            VanillaHudElement.EFFECTS -> { x = width; y = 0 }
            else -> return
        }
        VanillaHudPoses.begin(graphics, element, x, y)
    }

    // LOWEST：其他模组在 Post 里补画的内容也跟着平移
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onOverlayPost(event: RenderGuiOverlayEvent.Post) {
        val element = vanillaOverlays[event.overlay.id()] ?: return
        VanillaHudPoses.end(event.guiGraphics, element)
    }

    @SubscribeEvent
    fun onRenderGuiPost(event: RenderGuiEvent.Post) {
        VanillaHudPoses.popPending(event.guiGraphics)
    }

    // 隐藏与摆放共用。EXPERIENCE_BAR 不在表里：经验条与等级数字画在同一方法内，
    // 由 ExperienceBar1201Mixin 按调用分开隐藏与摆放
    private val vanillaOverlays: Map<ResourceLocation, VanillaHudElement> = mapOf(
        VanillaGuiOverlay.HOTBAR.id() to VanillaHudElement.HOTBAR,
        VanillaGuiOverlay.ITEM_NAME.id() to VanillaHudElement.SELECTED_ITEM_NAME,
        VanillaGuiOverlay.PLAYER_HEALTH.id() to VanillaHudElement.HEALTH,
        VanillaGuiOverlay.ARMOR_LEVEL.id() to VanillaHudElement.ARMOR,
        VanillaGuiOverlay.FOOD_LEVEL.id() to VanillaHudElement.FOOD,
        VanillaGuiOverlay.AIR_LEVEL.id() to VanillaHudElement.AIR,
        VanillaGuiOverlay.MOUNT_HEALTH.id() to VanillaHudElement.VEHICLE_HEALTH,
        VanillaGuiOverlay.JUMP_BAR.id() to VanillaHudElement.JUMP_BAR,
        VanillaGuiOverlay.CROSSHAIR.id() to VanillaHudElement.CROSSHAIR,
        VanillaGuiOverlay.POTION_ICONS.id() to VanillaHudElement.EFFECTS
    )

    private fun handleMouseInput(client: Minecraft) {
        val window = client.window.window
        val scale = client.window.guiScale
        val mx = (client.mouseHandler.xpos() / scale).toInt()
        val my = (client.mouseHandler.ypos() / scale).toInt()

        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
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
                    ForgePacketNetwork.sendKeyboardPacket(spec, true, inGui)
                }
            } else {
                pressedKeys.remove(spec)
                if (!wrapper.press) {
                    ForgePacketNetwork.sendKeyboardPacket(spec, false, inGui)
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
