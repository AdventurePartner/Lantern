package org.lantern.action

import net.minecraft.client.Minecraft
import org.lantern.core.action.PlayerActionDefs
import org.lantern.internal.util.KeySpec

/**
 * 玩家主动动作的按键轮询（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 每客户端 tick 调用）：
 * 按下边沿本地触发，不发包、不等服务端。与 keys.yml 的通道分开跟踪按下集，
 * 两者绑同一个键时互不干扰。NeoForge 在 NeoForgeClientEvents 里有同样的一份
 */
object PlayerActionInput {

    private val parsed = HashMap<String, KeySpec>()
    private val pressed = HashSet<String>()

    fun poll(client: Minecraft) {
        val definitions = PlayerActionDefs.definitions()
        if (definitions.isEmpty() || client.player == null) return
        // 界面打开时不触发：此时按键属于 UI 输入
        if (client.screen != null) {
            pressed.clear()
            return
        }
        val window = client.window.window
        definitions.forEach { def ->
            val down = parsed.getOrPut(def.keySpec) { KeySpec.parse(def.keySpec) }.isDown(window)
            if (down == def.id in pressed) return@forEach
            if (down) {
                pressed.add(def.id)
                PlayerActionStore.trigger(def)
            } else {
                pressed.remove(def.id)
            }
        }
    }

    fun clear() {
        parsed.clear()
        pressed.clear()
    }
}
