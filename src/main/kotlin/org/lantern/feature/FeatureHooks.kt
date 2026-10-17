package org.lantern.feature

import java.util.UUID
import net.minecraft.client.Minecraft
import org.lantern.action.PlayerActionInput
import org.lantern.action.PlayerActionStore
import org.lantern.animation.AnimationHost
import org.lantern.animation.GeckoLibMolang
import org.lantern.camera.control.CameraPortVanilla
import org.lantern.core.action.PlayerActionDefs
import org.lantern.core.anim.molang.MolangVariableStore
import org.lantern.core.bind.BindStore
import org.lantern.core.camera.CameraControl
import org.lantern.core.input.InputLockStore
import org.lantern.internal.network.NetworkParser
import org.lantern.model.handler.AnimationControlHandler

/**
 * Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 共用的可选能力注册：把 NetworkParser 的
 * 平台钩子接到 common-core 的状态机上。NeoForge 1.21.10 在 LanternNeoForge 里自己接
 * （本包被它排除），两边接的是同一批 common-core 对象。
 * 三个平台的收包处理都已调度到主线程，这里的处理器直接同步执行
 */
object FeatureHooks {

    fun register(animationEventSender: (UUID, String, String) -> Unit) {
        // packet 15 播控，以及播完 / 缺失 / 动作请求的 C2S 回报
        NetworkParser.animationControlHandler =
            { uuid, action, animation, transition, loop, speed, seek, uninterruptible, toCombat, library, seq, exit ->
                AnimationControlHandler.handle(
                    uuid, action, animation, transition, loop, speed, seek,
                    uninterruptible, toCombat, library, seq, exit
                )
            }
        NetworkParser.animationEventSender = animationEventSender
        // packet 17 molang 变量
        MolangVariableStore.compiler = GeckoLibMolang
        NetworkParser.molangVariableHandler = MolangVariableStore::update
        // packet 18 相机（shoulder 由 NetworkParser 直写 ShoulderCameraState，其余演出指令到这里）
        CameraControl.port = CameraPortVanilla
        NetworkParser.cameraActionHandler = CameraControl::handle
        // packet 19 玩家主动动作定义
        NetworkParser.playerActionHandler = PlayerActionDefs::load
        // packet 20 载体绑定
        NetworkParser.entityBindHandler = BindStore::handle
        // packet 21 输入锁：写表必须与并集缓存重算同线程，收包线程非主线程时调度过去
        NetworkParser.inputLockHandler = { obj ->
            val client = Minecraft.getInstance()
            if (client.isSameThread) {
                InputLockStore.handle(obj)
            } else {
                client.execute { InputLockStore.handle(obj) }
            }
        }
    }

    /**
     * 每客户端 tick（不受 GuiCanvas 打开与否影响）：播控重试、第一人称下本地玩家的动画驱动、
     * 翻滚位移，以及动作按键轮询（界面打开时它自己跳过）
     */
    fun onClientTick(client: Minecraft) {
        AnimationControlHandler.tick()
        AnimationHost.tickLocalPlayer()
        PlayerActionStore.tickDash()
        PlayerActionInput.poll(client)
    }

    fun onDisconnect() {
        PlayerActionInput.clear()
    }
}
