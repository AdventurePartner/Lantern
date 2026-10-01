package org.lantern.uix.hud

import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.panel.PanelWidgetImpl
import org.lantern.uix.widget.vanilla.VanillaElementWidgetImpl
import java.util.EnumMap
import java.util.EnumSet

/** 原版元素本帧要平移的距离（GUI 坐标）。 */
class PlacementOffset(@JvmField val dx: Float, @JvmField val dy: Float)

/**
 * `type: vanilla` 节点登记表。原版元素绘制前由平台挂钩按节点位置算出平移量、推入位姿，
 * 画完弹出（见 VanillaHudPoses）。偏移每次现算（布局走 LayoutCache 缓存），
 * 与 HUD 层的渲染先后、界面是否打开无关。
 */
object VanillaHudPlacement {

    const val STACKING_FIXED = "fixed"
    const val STACKING_VANILLA = "vanilla"

    private class Placement(val root: IWidget, val node: VanillaElementWidgetImpl)

    private var placements: Map<VanillaHudElement, Placement> = emptyMap()
    private var duplicates: Set<VanillaHudElement> = emptySet()

    // 已推入、尚未弹出的元素；不随配置清空，保证位姿栈始终能配平
    private val pushed = BooleanArray(VanillaHudElement.entries.size)

    /** 按 HUD 的 index 顺序收集 vanilla 节点；同一元素先到先得，其余记为重复。 */
    fun rebuild(hudRoots: List<IWidget>) {
        val found = EnumMap<VanillaHudElement, Placement>(VanillaHudElement::class.java)
        val repeated = EnumSet.noneOf(VanillaHudElement::class.java)
        hudRoots.forEach { root -> collect(root, root, found, repeated) }
        placements = found
        duplicates = repeated
    }

    fun clear() {
        placements = emptyMap()
        duplicates = emptySet()
    }

    /** 被多个 vanilla 节点声明的元素（只有第一个生效）。 */
    fun duplicates(): Set<VanillaHudElement> = duplicates

    private fun collect(
        root: IWidget,
        widget: IWidget,
        found: MutableMap<VanillaHudElement, Placement>,
        repeated: MutableSet<VanillaHudElement>
    ) {
        if (widget is VanillaElementWidgetImpl) {
            val element = widget.element ?: return
            if (element in found) repeated.add(element) else found[element] = Placement(root, widget)
        }
        if (widget is PanelWidgetImpl) {
            widget.children.forEach { collect(root, it, found, repeated) }
        }
    }

    /**
     * [actualX]/[actualY] 是元素本帧在原版里的参考点，含义随 [VanillaHudElement.align]。
     * `stacking: fixed`（默认）对齐实际参考点：元素钉在节点上，原版元素间的避让失效；
     * `stacking: vanilla` 对齐原版默认布局参考点：相当于整体平移，原版的动态避让照常。
     * 未摆放或无需平移时返回 null。
     */
    @JvmStatic
    fun offset(
        element: VanillaHudElement,
        guiWidth: Int,
        guiHeight: Int,
        actualX: Int,
        actualY: Int
    ): PlacementOffset? {
        val placement = placements[element] ?: return null
        LayoutCache.getOrCompute(placement.root, guiWidth, guiHeight)
        val node = placement.node
        val nodeX = WidgetGeometry.absoluteX(node)
        val targetX = when (element.align) {
            VanillaHudAlign.TOP_LEFT -> nodeX
            VanillaHudAlign.TOP_CENTER -> nodeX + WidgetGeometry.width(node, element.naturalWidth) / 2
            VanillaHudAlign.TOP_RIGHT -> nodeX + WidgetGeometry.width(node, element.naturalWidth)
        }
        val targetY = WidgetGeometry.absoluteY(node)
        val followVanilla = node.style.getString(StyleProperty.STACKING, STACKING_FIXED)
            .equals(STACKING_VANILLA, ignoreCase = true)
        val referenceX = if (followVanilla) element.nominalX(guiWidth) else actualX
        val referenceY = if (followVanilla) element.nominalY(guiHeight) else actualY
        val dx = targetX - referenceX
        val dy = targetY - referenceY
        if (dx == 0 && dy == 0) return null
        return PlacementOffset(dx.toFloat(), dy.toFloat())
    }

    @JvmStatic
    fun markPushed(element: VanillaHudElement) {
        pushed[element.ordinal] = true
    }

    /** 该元素是否有待弹出的位姿；返回后即清除标记。 */
    @JvmStatic
    fun consumePushed(element: VanillaHudElement): Boolean {
        val wasPushed = pushed[element.ordinal]
        pushed[element.ordinal] = false
        return wasPushed
    }

    /** 清除全部待弹出标记，返回需要补弹的层数。 */
    @JvmStatic
    fun consumeAllPending(): Int {
        var count = 0
        for (i in pushed.indices) {
            if (pushed[i]) {
                pushed[i] = false
                count++
            }
        }
        return count
    }
}
