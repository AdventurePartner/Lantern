package org.lantern.uix.renderer

/**
 * 当前 widget 树的渲染场景。slot 节点在容器 overlay 里是"位置声明"（原版槽位自己画），
 * 在 HUD 里是玩家背包镜像——不能用 `Minecraft.screen` 判断：原版层 HUD 在箱子等容器界面
 * 打开时仍会绘制，那时 screen 是容器但节点必须按镜像渲染。
 */
object UixRenderContext {

    /** HudCanvas 绘制期间为 true。 */
    var hud: Boolean = false
        private set

    fun <T> inHud(block: () -> T): T {
        val previous = hud
        hud = true
        try {
            return block()
        } finally {
            hud = previous
        }
    }
}
