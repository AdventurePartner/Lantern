package org.lantern.platform

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import org.lantern.animation.AnimationHost
import org.lantern.costume.handler.CostumeHandler
import org.lantern.internal.chat.ChatChannelHandler
import org.lantern.internal.chat.ChatChannelTabsRenderer
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.placeholder.PlaceholderStore
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.model.handler.RendererHandler
import org.lantern.model.renderstate.AnimationControlStore
import org.lantern.uix.canvas.impl.GuiCanvas
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.input.FocusManager
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.OverlayRenderer
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

object NeoForgeClientEvents {
    private val pressedKeys = mutableSetOf<String>()
    private val parsedKeys = mutableMapOf<String, ParsedKey>()
    private var wasMouseDown = false

    fun register() {
        NeoForge.EVENT_BUS.addListener(
            ClientTickEvent.Post::class.java,
            Consumer<ClientTickEvent.Post>(::onClientTick)
        )
        NeoForge.EVENT_BUS.addListener(
            ClientPlayerNetworkEvent.LoggingOut::class.java,
            Consumer<ClientPlayerNetworkEvent.LoggingOut>(::onLoggingOut)
        )
        NeoForge.EVENT_BUS.addListener(
            EntityLeaveLevelEvent::class.java,
            Consumer<EntityLeaveLevelEvent>(::onEntityLeaveLevel)
        )
        NeoForge.EVENT_BUS.addListener(
            ScreenEvent.Render.Post::class.java,
            Consumer<ScreenEvent.Render.Post>(::onScreenRender)
        )
        NeoForge.EVENT_BUS.addListener(
            ScreenEvent.MouseButtonPressed.Pre::class.java,
            Consumer<ScreenEvent.MouseButtonPressed.Pre>(::onMousePressed)
        )
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
        TextureHandler.tick()
        if (client.screen is GuiCanvas) {
            wasMouseDown = false
            return
        }
        handleKeyboardInput(client)
        handleMouseInput(client)
    }

    private fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        NeoForgePacketNetwork.clear()
        ChatChannelHandler.clear()
        ResourceHandler.clearSession()
        TextureHandler.reload()
        UiScreenStorage.clear()
        PlaceholderStore.clear()
        LayoutCache.clear()
        FocusManager.blur()
        pressedKeys.clear()
        parsedKeys.clear()
        wasMouseDown = false
    }

    private fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        if (event.level.isClientSide) {
            CostumeHandler.removePlayer(event.entity.uuid)
            // 淘汰 Lantern 实体模型的 per-UUID 状态，防止长期游玩内存无上限增长；
            // 渲染器懒重建，实体换维度重新进入视距时会自动恢复
            RendererHandler.evict(event.entity.uuid)
            AnimationHost.remove(event.entity.uuid)
            AnimationControlStore.stop(event.entity.uuid, null)
        }
    }

    private fun onScreenRender(event: ScreenEvent.Render.Post) {
        OverlayRenderer.tryRenderOverlay(
            event.screen,
            event.guiGraphics,
            event.mouseX,
            event.mouseY,
            event.partialTick
        )
        if (event.screen is ChatScreen) {
            ChatChannelTabsRenderer.render(event.guiGraphics, event.mouseX, event.mouseY)
        }
    }

    private fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            event.screen is ChatScreen &&
            ChatChannelTabsRenderer.handleClick(event.mouseX.toInt(), event.mouseY.toInt())
        ) {
            event.isCanceled = true
            return
        }
        if (OverlayRenderer.handleClick(event.screen, event.mouseX, event.mouseY, event.button)) {
            event.isCanceled = true
        }
    }

    private fun handleMouseInput(client: Minecraft) {
        val window = client.getWindow().handle()
        val scale = client.getWindow().guiScale
        val mouseX = (client.mouseHandler.xpos() / scale).toInt()
        val mouseY = (client.mouseHandler.ypos() / scale).toInt()
        val leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        if (leftDown && !wasMouseDown) {
            FocusManager.blur()
            UiScreenStorage.getAllOfType(ScreenType.HUD).values.forEach { root ->
                EventDispatcher.dispatchClick(root, mouseX, mouseY)
            }
        }
        wasMouseDown = leftDown
        UiScreenStorage.getAllOfType(ScreenType.HUD).values.forEach { root ->
            EventDispatcher.updateHover(root, mouseX, mouseY)
        }
    }

    private fun handleKeyboardInput(client: Minecraft) {
        val window = client.getWindow().handle()
        val inGui = client.screen != null
        ResourceHandler.getKeyboards().forEach { (spec, wrapper) ->
            val key = parsedKeys.getOrPut(spec) { parseKeySpec(spec) }
            val isDown = isDown(window, key)
            val wasDown = spec in pressedKeys
            if (isDown == wasDown) return@forEach
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
        if (key.keyCode == GLFW.GLFW_KEY_UNKNOWN) return false
        if (key.requiredMods and GLFW.GLFW_MOD_CONTROL != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)
        ) return false
        if (key.requiredMods and GLFW.GLFW_MOD_SHIFT != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)
        ) return false
        if (key.requiredMods and GLFW.GLFW_MOD_ALT != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)
        ) return false
        if (key.requiredMods and GLFW.GLFW_MOD_SUPER != 0 &&
            !isAnyDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)
        ) return false
        return GLFW.glfwGetKey(window, key.keyCode) == GLFW.GLFW_PRESS
    }

    private fun isAnyDown(window: Long, left: Int, right: Int): Boolean =
        GLFW.glfwGetKey(window, left) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, right) == GLFW.GLFW_PRESS

    private fun parseKeySpec(spec: String): ParsedKey {
        val parts = spec.lowercase().split('+').map(String::trim).filter(String::isNotEmpty)
        var modifiers = 0
        var main = ""
        parts.forEach { part ->
            when (part) {
                "ctrl", "control" -> modifiers = modifiers or GLFW.GLFW_MOD_CONTROL
                "shift" -> modifiers = modifiers or GLFW.GLFW_MOD_SHIFT
                "alt" -> modifiers = modifiers or GLFW.GLFW_MOD_ALT
                "super", "win", "meta", "cmd" -> modifiers = modifiers or GLFW.GLFW_MOD_SUPER
                else -> main = part
            }
        }
        val keyCode = when {
            main.length == 1 && main[0] in 'a'..'z' -> GLFW.GLFW_KEY_A + (main[0] - 'a')
            main.length == 1 && main[0] in '0'..'9' -> GLFW.GLFW_KEY_0 + (main[0] - '0')
            main.startsWith('f') && main.drop(1).toIntOrNull() in 1..25 ->
                GLFW.GLFW_KEY_F1 + (main.drop(1).toInt() - 1)
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
            main == "ctrl" || main == "control" -> GLFW.GLFW_KEY_LEFT_CONTROL
            main == "shift" -> GLFW.GLFW_KEY_LEFT_SHIFT
            main == "alt" -> GLFW.GLFW_KEY_LEFT_ALT
            main == "super" || main == "win" || main == "meta" || main == "cmd" -> GLFW.GLFW_KEY_LEFT_SUPER
            else -> GLFW.GLFW_KEY_UNKNOWN
        }
        return ParsedKey(keyCode, modifiers)
    }
}

private data class ParsedKey(val keyCode: Int, val requiredMods: Int)
