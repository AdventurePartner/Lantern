package org.lantern.uix.renderer

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import org.lantern.internal.storage.OverlayMatch
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.panel.PanelWidgetImpl
import org.lantern.uix.widget.slot.SlotWidgetImpl
import java.util.WeakHashMap

/**
 * 槽位重定位：容器界面 overlay 中的 slot 节点是"位置声明"——
 * 把 menu.slots[N] 的原版槽位移动到节点布局位置（容器锚定坐标系，支持嵌套 panel 偏移累加）。
 * 原版的渲染、点击命中、拖拽、shift 快移、tooltip 全部实时读取 Slot.x/y，移动后原位逻辑自动跟随。
 *
 * 每帧幂等应用：先把已触碰 menu 的全部槽位恢复原始坐标，再按声明写入——
 * 配置热更新（坐标变更/节点删除）在下一帧即时生效；[resetAll] 供配置整体重载时调用。
 */
object SlotLayoutManager {

    private const val HIDDEN = -9999

    // menu -> 原始 x/y（首次触碰时快照；WeakHashMap 随 menu 实例回收）
    private val originals = WeakHashMap<AbstractContainerMenu, IntArray>()

    fun apply(
        container: AbstractContainerScreen<*>,
        panel: ContainerPanelBridge?,
        matches: List<OverlayMatch>
    ) {
        if (panel == null) return
        if (matches.none { hasSlotNode(it.rootWidget) }) return
        val menu = container.menu

        restore(menu)

        for (match in matches) {
            if (!hasSlotNode(match.rootWidget)) continue
            OverlayRenderer.layoutMatch(match, container, panel)
            walk(match.rootWidget, 0, 0) { widget, absX, absY ->
                if (widget.slotIndex !in menu.slots.indices) return@walk
                val bridge = menu.slots[widget.slotIndex] as? SlotMutableBridge ?: return@walk
                if (widget.style.getBoolean(StyleProperty.VISIBLE)) {
                    bridge.`lantern$setSlotX`(absX - panel.`lantern$getLeftPos`())
                    bridge.`lantern$setSlotY`(absY - panel.`lantern$getTopPos`())
                } else {
                    bridge.`lantern$setSlotX`(HIDDEN)
                    bridge.`lantern$setSlotY`(HIDDEN)
                }
            }
        }
    }

    /** 恢复所有已触碰 menu 的原始槽位坐标（配置整体重载时调用，防止旧声明残留）。 */
    fun resetAll() {
        for ((menu, _) in originals) restore(menu)
        originals.clear()
    }

    /** 恢复单个 menu 的原始坐标；首次触碰时快照原始值。 */
    private fun restore(menu: AbstractContainerMenu) {
        val snapshot = originals.getOrPut(menu) {
            IntArray(menu.slots.size * 2).also { arr ->
                menu.slots.forEachIndexed { i, slot ->
                    arr[i * 2] = slot.x
                    arr[i * 2 + 1] = slot.y
                }
            }
        }
        menu.slots.forEachIndexed { i, slot ->
            val bridge = slot as? SlotMutableBridge ?: return@forEachIndexed
            bridge.`lantern$setSlotX`(snapshot[i * 2])
            bridge.`lantern$setSlotY`(snapshot[i * 2 + 1])
        }
    }

    private fun hasSlotNode(widget: IWidget): Boolean {
        if (widget is SlotWidgetImpl && widget.slotIndex >= 0) return true
        if (widget is PanelWidgetImpl) {
            for (child in widget.children) if (hasSlotNode(child)) return true
        }
        return false
    }

    /**
     * 递归累加布局坐标：根 rect 为绝对屏幕坐标（容器锚定时已含 leftPos/topPos），
     * 子组件 rect 为父容器局部坐标（与 EventDispatcher 命中测试同一坐标系约定）。
     */
    private fun walk(widget: IWidget, accX: Int, accY: Int, visit: (SlotWidgetImpl, Int, Int) -> Unit) {
        val rect = LayoutCache.findRect(widget)
        val x = accX + (rect?.x ?: widget.style.getInt(StyleProperty.X))
        val y = accY + (rect?.y ?: widget.style.getInt(StyleProperty.Y))
        if (widget is SlotWidgetImpl) visit(widget, x, y)
        if (widget is PanelWidgetImpl) {
            for (child in widget.children) walk(child, x, y, visit)
        }
    }
}
