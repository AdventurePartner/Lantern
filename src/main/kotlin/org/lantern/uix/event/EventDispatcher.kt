package org.lantern.uix.event

import org.lantern.uix.IComponent
import org.lantern.uix.layout.LayoutRect
import org.lantern.uix.layout.Overflow
import org.lantern.uix.layout.WidgetGeometry
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.slot.HotbarSelectionWidgetImpl
import java.util.Collections

/**
 * 鼠标命中：区域与渲染器一致（[WidgetGeometry.box]），不可见容器的子组件不画也不参与命中；
 * 兄弟组件按列表顺序绘制、后画的盖在上面，所以点击与查找从最后一个子组件开始。
 */
object EventDispatcher {

    private val hoveredComponents: MutableSet<IComponent> =
        Collections.newSetFromMap(java.util.IdentityHashMap())

    // Reusable scratch set, swapped each frame to avoid per-frame allocation
    private val scratchSet: MutableSet<IComponent> =
        Collections.newSetFromMap(java.util.IdentityHashMap())

    /**
     * 分发点击并返回是否命中了可见控件且事件被消费。
     * overlay 依据该返回值决定是否拦截原版界面的鼠标事件。
     */
    fun dispatchClick(root: IComponent, mouseX: Int, mouseY: Int, button: Int = 0): Boolean =
        dispatchClickRecursive(root, mouseX, mouseY, button)

    private fun dispatchClickRecursive(
        component: IComponent,
        mouseX: Int,
        mouseY: Int,
        button: Int
    ): Boolean {
        if (isClippedOut(component, mouseX, mouseY) || !isVisible(component)) return false
        val rect = findRect(component)
        val cx = rect?.x ?: styleX(component)
        val cy = rect?.y ?: styleY(component)

        // Children first (depth-first, topmost child first)
        for (child in component.getChildren().asReversed()) {
            if (dispatchClickRecursive(child, mouseX - cx, mouseY - cy, button)) {
                return true
            }
        }

        // Hit test on current component
        if (rect == null || !contains(rect, mouseX, mouseY)) return false
        val event = MouseEvent(mouseX - rect.x, mouseY - rect.y, button)
        component.dispatchClick(event)
        return event.consumed
    }

    /**
     * 鼠标下方第一个满足 [filter] 的可见组件（子组件优先、上层优先），坐标换算与裁切规则同点击分发。
     */
    fun findDeepest(root: IComponent, mouseX: Int, mouseY: Int, filter: (IComponent) -> Boolean): IComponent? {
        if (isClippedOut(root, mouseX, mouseY) || !isVisible(root)) return null
        val rect = findRect(root)
        val cx = rect?.x ?: styleX(root)
        val cy = rect?.y ?: styleY(root)
        for (child in root.getChildren().asReversed()) {
            findDeepest(child, mouseX - cx, mouseY - cy, filter)?.let { return it }
        }
        if (!filter(root) || rect == null) return null
        return root.takeIf { contains(rect, mouseX, mouseY) }
    }

    fun updateHover(root: IComponent, mouseX: Int, mouseY: Int) {
        scratchSet.clear()
        collectHovered(root, mouseX, mouseY, scratchSet)

        // Components that were hovered but no longer are -> leave
        val iterator = hoveredComponents.iterator()
        while (iterator.hasNext()) {
            val comp = iterator.next()
            if (comp !in scratchSet) {
                iterator.remove()
                comp.dispatchMouseLeave(MouseEvent(mouseX, mouseY))
            }
        }

        // Components that are now hovered but weren't before -> enter
        for (comp in scratchSet) {
            if (comp !in hoveredComponents) {
                hoveredComponents.add(comp)
                comp.dispatchMouseEnter(MouseEvent(mouseX, mouseY))
            }
        }
    }

    private fun collectHovered(
        component: IComponent,
        mouseX: Int,
        mouseY: Int,
        result: MutableSet<IComponent>
    ) {
        if (isClippedOut(component, mouseX, mouseY) || !isVisible(component)) return
        val rect = findRect(component)
        val cx = rect?.x ?: styleX(component)
        val cy = rect?.y ?: styleY(component)

        // Check children with local coordinates
        for (child in component.getChildren()) {
            collectHovered(child, mouseX - cx, mouseY - cy, result)
        }

        // Hit test on current component
        if (rect != null && contains(rect, mouseX, mouseY)) {
            result.add(component)
        }
    }

    /**
     * 组件区域，也是子组件的坐标原点；hotbar-selection 画在选中槽位上而不在自己的区域里，不参与命中。
     * 宽或高为 0 的区域由 [contains] 判为不命中，但原点仍要用它（比如子组件全是 absolute 的 flex 容器）。
     */
    private fun findRect(component: IComponent): LayoutRect? {
        if (component !is IWidget || component is HotbarSelectionWidgetImpl) return null
        return WidgetGeometry.box(component)
    }

    private fun contains(rect: LayoutRect, mouseX: Int, mouseY: Int): Boolean =
        mouseX in rect.x until rect.x + rect.width && mouseY in rect.y until rect.y + rect.height

    /** 鼠标落在 overflow: hidden 组件的区域外：它和子组件都已被裁掉，不参与命中。 */
    private fun isClippedOut(component: IComponent, mouseX: Int, mouseY: Int): Boolean =
        component is IWidget && Overflow.isHidden(component) && !Overflow.contains(component, mouseX, mouseY)

    // 未参与布局的容器在 PanelRenderer 里按 style 的 x/y 平移子组件，命中换算要与之一致
    private fun styleX(component: IComponent): Int =
        (component as? IWidget)?.style?.getInt(StyleProperty.X) ?: 0

    private fun styleY(component: IComponent): Int =
        (component as? IWidget)?.style?.getInt(StyleProperty.Y) ?: 0

    private fun isVisible(component: IComponent): Boolean {
        if (component is IWidget) {
            return component.style.getBoolean(StyleProperty.VISIBLE)
        }
        return true
    }
}
