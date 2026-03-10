package org.lantern.uix.event

import org.lantern.uix.IComponent
import org.lantern.uix.layout.LayoutCache
import org.lantern.uix.layout.LayoutRect
import org.lantern.uix.style.StyleProperty
import org.lantern.uix.widget.IWidget
import java.util.Collections

object EventDispatcher {

    private val hoveredComponents: MutableSet<IComponent> =
        Collections.newSetFromMap(java.util.IdentityHashMap())

    fun dispatchClick(root: IComponent, mouseX: Int, mouseY: Int, button: Int = 0) {
        dispatchClickRecursive(root, mouseX, mouseY, button)
    }

    private fun dispatchClickRecursive(
        component: IComponent,
        mouseX: Int,
        mouseY: Int,
        button: Int
    ): Boolean {
        val rect = findRect(component)
        val cx = rect?.x ?: 0
        val cy = rect?.y ?: 0

        // Children first (depth-first, child-priority)
        for (child in component.getChildren()) {
            if (dispatchClickRecursive(child, mouseX - cx, mouseY - cy, button)) {
                return true
            }
        }

        // Hit test on current component
        if (!isVisible(component)) return false
        val x = rect?.x ?: 0
        val y = rect?.y ?: 0
        val w = rect?.width ?: 0
        val h = rect?.height ?: 0
        if (w == 0 && h == 0) return false

        if (mouseX in x until x + w && mouseY in y until y + h) {
            val event = MouseEvent(mouseX - x, mouseY - y, button)
            component.dispatchClick(event)
            return event.consumed
        }
        return false
    }

    fun updateHover(root: IComponent, mouseX: Int, mouseY: Int) {
        val currentHovered: MutableSet<IComponent> =
            Collections.newSetFromMap(java.util.IdentityHashMap())
        collectHovered(root, mouseX, mouseY, currentHovered)

        // Components that were hovered but no longer are -> leave
        val iterator = hoveredComponents.iterator()
        while (iterator.hasNext()) {
            val comp = iterator.next()
            if (comp !in currentHovered) {
                iterator.remove()
                comp.dispatchMouseLeave(MouseEvent(mouseX, mouseY))
            }
        }

        // Components that are now hovered but weren't before -> enter
        for (comp in currentHovered) {
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
        val rect = findRect(component)
        val cx = rect?.x ?: 0
        val cy = rect?.y ?: 0

        // Check children with local coordinates
        for (child in component.getChildren()) {
            collectHovered(child, mouseX - cx, mouseY - cy, result)
        }

        // Hit test on current component
        if (!isVisible(component)) return
        val x = rect?.x ?: 0
        val y = rect?.y ?: 0
        val w = rect?.width ?: 0
        val h = rect?.height ?: 0
        if (w == 0 && h == 0) return

        if (mouseX in x until x + w && mouseY in y until y + h) {
            result.add(component)
        }
    }

    private fun findRect(component: IComponent): LayoutRect? {
        if (component !is IWidget) return null
        val cached = LayoutCache.findRect(component)
        if (cached != null) return cached
        val x = component.style.getInt(StyleProperty.X)
        val y = component.style.getInt(StyleProperty.Y)
        val w = component.style.getInt(StyleProperty.WIDTH)
        val h = component.style.getInt(StyleProperty.HEIGHT)
        if (w == 0 && h == 0) return null
        return LayoutRect(x, y, w, h)
    }

    private fun isVisible(component: IComponent): Boolean {
        if (component is IWidget) {
            return component.style.getBoolean(StyleProperty.VISIBLE)
        }
        return true
    }
}
