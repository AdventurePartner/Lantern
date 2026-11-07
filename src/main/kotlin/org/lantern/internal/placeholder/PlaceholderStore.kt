package org.lantern.internal.placeholder

import org.lantern.uix.widget.ITextHolder
import org.lantern.uix.widget.IWidget
import org.lantern.uix.IComponent

/**
 * Client-side store that manages PlaceholderAPI text replacement for UI screens.
 *
 * On the first [update] call for a given screen, the widget tree is traversed
 * to discover all [ITextHolder] texts and widget tooltips that contain `%`
 * characters. Each original string is captured as a *template*. Subsequent
 * updates simply perform string replacement against these templates.
 *
 * Thread safety: all calls happen on the render thread (via NetworkParser),
 * and the renderers read `widget.text` / `widget.tooltip` on the same thread — no races.
 */
object PlaceholderStore {

    /** screenId → list of (templateText, setter) */
    private val bindings = mutableMapOf<String, MutableList<Binding>>()

    private class Binding(val template: String, val apply: (String) -> Unit)

    /**
     * Apply placeholder values to all text / button texts and tooltips in the given screen.
     *
     * @param screenId   the screen identifier (from YAML `id` field)
     * @param rootWidget the root widget of the screen's widget tree
     * @param values     resolved placeholder map, e.g. {"%player_name%" → "Steve"}
     */
    fun update(screenId: String, rootWidget: IWidget, values: Map<String, String>) {
        val holders = bindings.getOrPut(screenId) {
            mutableListOf<Binding>().also { list ->
                collectBindings(rootWidget, list)
            }
        }

        for (binding in holders) {
            var resolved = binding.template
            for ((key, value) in values) {
                resolved = resolved.replace(key, value)
            }
            binding.apply(resolved)
        }
    }

    private fun collectBindings(component: IComponent, out: MutableList<Binding>) {
        if (component is ITextHolder && component.text.contains('%')) {
            out.add(Binding(component.text) { component.text = it })
        }
        if (component is IWidget && component.tooltip.contains('%')) {
            out.add(Binding(component.tooltip) { component.tooltip = it })
        }
        for (child in component.getChildren()) {
            collectBindings(child, out)
        }
    }

    /**
     * Clear all cached bindings. Must be called when screens are re-parsed
     * (e.g. on Packet 5 re-delivery after /lantern reload).
     */
    fun clear() {
        bindings.clear()
    }
}
