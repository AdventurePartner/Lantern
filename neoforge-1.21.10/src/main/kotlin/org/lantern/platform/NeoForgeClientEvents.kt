package org.lantern.platform

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.lantern.animation.AnimationHost
import org.lantern.costume.handler.CostumeHandler
import org.lantern.internal.chat.ChatChannelHandler
import org.lantern.internal.chat.ChatChannelTabsRenderer
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.mixin.accessor.ClientInputAccessor
import org.lantern.internal.placeholder.PlaceholderStore
import org.lantern.internal.storage.ScreenType
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.model.handler.RendererHandler
import org.lantern.core.anim.control.AnimationControlStore
import org.lantern.uix.canvas.impl.GuiCanvas
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.input.FocusManager
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.renderer.OverlayRenderer
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

object NeoForgeClientEvents {
    private val pressedKeys = mutableSetOf<String>()
    /** 玩家主动动作的按下集（按动作 id 跟踪，与 keys.yml 通道分开） */
    private val actionPressedKeys = mutableSetOf<String>()
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
        // 输入锁的移动/跳跃/潜行压制点。
        // 不放在 ClientTickEvent 里：KeyboardInput.tick 每 tick 重新从按键状态
        // 覆写 keyPresses 与 moveVector，tick 回调里写进去的值下一 tick 就被抹掉。
        // MovementInputUpdateEvent 恰好在 input.tick() 之后、移动被消费之前触发
        NeoForge.EVENT_BUS.addListener(
            MovementInputUpdateEvent::class.java,
            Consumer<MovementInputUpdateEvent>(::onMovementInput)
        )
        // attack / use 压制：三条路径（startAttack、continueAttack、startUseItem）
        // 都经由这一个可取消事件，取消即等于该键没按下——挥击不发生，连招也不会起
        NeoForge.EVENT_BUS.addListener(
            InputEvent.InteractionKeyMappingTriggered::class.java,
            Consumer<InputEvent.InteractionKeyMappingTriggered>(::onInteractionKey)
        )
        // 世界图片：AfterEntities 是唯一带非空 PoseStack 的阶段，与实体同批、
        // 被地形深度正确遮挡；partialTick 在 1.21.2+ 走 DeltaTracker
        NeoForge.EVENT_BUS.addListener(
            RenderLevelStageEvent.AfterEntities::class.java,
            Consumer<RenderLevelStageEvent.AfterEntities>(::onAfterEntities)
        )
    }

    private fun onAfterEntities(event: RenderLevelStageEvent.AfterEntities) {
        val client = Minecraft.getInstance()
        val buffers = client.renderBuffers().bufferSource()
        val partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false)
        org.lantern.worldimage.WorldImageRenderer.render(
            event.poseStack, buffers, client.gameRenderer.getMainCamera(), partialTick
        )
        buffers.endBatch()
    }

    /**
     * 输入锁：移动 / 跳跃 / 潜行。
     *
     * keyPresses 和 moveVector 必须一起清：前者决定跳跃、潜行与冲刺判定，
     * 后者才是真正驱动位移的量（LocalPlayer.applyInput 用它算 xxa/zza）。
     * 只清一个的话锁形同虚设
     */
    private fun onMovementInput(event: MovementInputUpdateEvent) {
        val locks = org.lantern.core.input.InputLockStore.activeLocks()
        if (locks.isEmpty()) return
        val lockMove = "move" in locks
        val lockJump = "jump" in locks
        val lockSneak = "sneak" in locks
        if (!lockMove && !lockJump && !lockSneak) return

        val input = event.input
        val keys = input.keyPresses
        input.keyPresses = Input(
            !lockMove && keys.forward(),
            !lockMove && keys.backward(),
            !lockMove && keys.left(),
            !lockMove && keys.right(),
            !lockJump && keys.jump(),
            !lockSneak && keys.shift(),
            keys.sprint()
        )
        if (lockMove) {
            (input as ClientInputAccessor).`lantern$setMoveVector`(Vec2.ZERO)
        }
    }

    /**
     * 输入锁：攻击键与使用键。
     *
     * 取消事件只跳过攻击/使用的结算，Minecraft.startAttack 之后仍按 shouldSwingHand()
     * 决定要不要 swing()——挥手照发的话，swinging 上升沿一到，连招照样起。
     * 所以取消的同时必须把挥手也关掉，锁才是真的锁
     */
    private fun onInteractionKey(event: InputEvent.InteractionKeyMappingTriggered) {
        val locks = org.lantern.core.input.InputLockStore.activeLocks()
        if (locks.isEmpty()) return
        if (event.isAttack && "attack" in locks) {
            event.isCanceled = true
            event.setSwingHand(false)
            return
        }
        if (event.isUseItem && "use" in locks) {
            event.isCanceled = true
            event.setSwingHand(false)
        }
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
        TextureHandler.tick()
        if (client.screen is GuiCanvas) {
            wasMouseDown = false
            return
        }
        // 播控重试：实体或自定义名还没同步的指令按 tick 重试几次再放弃
        org.lantern.model.handler.AnimationControlHandler.tick()
        // 第一人称不渲染自己，播放器就不会被渲染回调驱动——时间轴、到期、槽位释放全部冻结。
        // 这里按 tick 补一次驱动，让本地玩家的状态机在看不见自己时照常走
        AnimationHost.tickLocalPlayer()
        handleKeyboardInput(client)
        handlePlayerActions(client)
        // 翻滚等动作的位移逐 tick 驱动，与动画同步收尾
        org.lantern.action.PlayerActionStore.tickDash()
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
        actionPressedKeys.clear()
        org.lantern.action.PlayerActionStore.clearDash()
        // 动作定义随会话作废：留着的话在新服首包到达前，旧服的翻滚键和连招还在生效，
        // server-checked 条目还会朝新服发请求
        org.lantern.action.PlayerActionStore.clear()
        org.lantern.model.handler.AnimationControlHandler.clearPending()
        org.lantern.core.anim.molang.MolangVariableStore.clear()
        // 绑定是会话内状态，换服必须归零，否则新服的实体会带着旧绑定渲染
        org.lantern.core.bind.BindStore.clear()
        // 输入锁同理，不跨服残留
        org.lantern.core.input.InputLockStore.clear()
        parsedKeys.clear()
        wasMouseDown = false
        // 相机演出状态（lock/shake/fov/offset）不跨服残留
        org.lantern.core.camera.CameraControl.hardReset()
        // 越肩偏移是服务端会话态下发，同样归零
        org.lantern.core.camera.ShoulderCameraState.clear()
    }

    private fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        if (event.level.isClientSide) {
            // 本地玩家离场（换维度/重生）：进行中的翻滚位移不能带到新实体上继续推
            if (event.entity === Minecraft.getInstance().player) {
                org.lantern.action.PlayerActionStore.clearDash()
            }
            CostumeHandler.removePlayer(event.entity.uuid)
            // 淘汰 Lantern 实体模型的 per-UUID 状态，防止长期游玩内存无上限增长；
            // 渲染器懒重建，实体换维度重新进入视距时会自动恢复
            RendererHandler.evict(event.entity.uuid)
            AnimationHost.remove(event.entity.uuid)
            org.lantern.core.bind.BindStore.remove(event.entity.uuid)
            AnimationControlStore.stop(event.entity.uuid, null)
            org.lantern.core.anim.molang.MolangVariableStore.remove(event.entity.uuid)
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

    /**
     * 玩家主动动作的按键轮询（翻滚等）：按下边沿本地触发，不发包、不等服务端。
     * 与 keys.yml 的通道分开跟踪按下集，两者绑同一个键时互不干扰
     */
    private fun handlePlayerActions(client: Minecraft) {
        val definitions = org.lantern.core.action.PlayerActionDefs.definitions()
        if (definitions.isEmpty()) return
        if (client.player == null) return
        val window = client.getWindow().handle()
        // 界面打开时不触发：此时按键属于 UI 输入
        if (client.screen != null) {
            actionPressedKeys.clear()
            return
        }
        definitions.forEach { def ->
            val key = parsedKeys.getOrPut(def.keySpec) { parseKeySpec(def.keySpec) }
            val isDown = isDown(window, key)
            val wasDown = def.id in actionPressedKeys
            if (isDown == wasDown) return@forEach
            if (isDown) {
                actionPressedKeys.add(def.id)
                org.lantern.action.PlayerActionStore.trigger(def)
            } else {
                actionPressedKeys.remove(def.id)
            }
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
        // 只写了修饰键、没写主键时（如 "alt"、"ctrl"），把它本身当主键：
        // 否则 main 留空、keyCode 落到 UNKNOWN，isDown 首行即返回 false，
        // 这类配置永远不会触发。带主键的组合（"alt+q"）不受影响
        if (main.isEmpty() && parts.size == 1) {
            main = parts[0]
            modifiers = 0
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
            main == "ctrl" || main == "control" || main == "left_ctrl" || main == "lctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL
            main == "right_ctrl" || main == "rctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL
            main == "shift" || main == "left_shift" || main == "lshift" -> GLFW.GLFW_KEY_LEFT_SHIFT
            main == "right_shift" || main == "rshift" -> GLFW.GLFW_KEY_RIGHT_SHIFT
            main == "alt" || main == "left_alt" || main == "lalt" -> GLFW.GLFW_KEY_LEFT_ALT
            main == "right_alt" || main == "ralt" -> GLFW.GLFW_KEY_RIGHT_ALT
            main == "super" || main == "win" || main == "meta" || main == "cmd" -> GLFW.GLFW_KEY_LEFT_SUPER
            else -> GLFW.GLFW_KEY_UNKNOWN
        }
        return ParsedKey(keyCode, modifiers)
    }
}

private data class ParsedKey(val keyCode: Int, val requiredMods: Int)
