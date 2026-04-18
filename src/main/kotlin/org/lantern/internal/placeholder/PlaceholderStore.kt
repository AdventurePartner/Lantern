package org.lantern.internal.placeholder

import org.lantern.uix.widget.ITextHolder
import org.lantern.uix.widget.IWidget
import org.lantern.uix.IComponent

/**
 * Client-side store that manages PlaceholderAPI text replacement for UI screens.
 *
 * On the first [update] call for a given screen, the widget tree is traversed
 * to discover all [ITextHolder] widgets whose text contains `%` characters.
 * Each widget's original text is captured as a *template*. Subsequent updates
 * simply perform string replacement against these templates.
 *
 * Thread safety: all calls happen on the render thread (via NetworkParser),
 * and TextRenderer reads `widget.text` on the same thread — no races.
 */
object PlaceholderStore {

    /** screenId → list of (widget, templateText) */
    private val bindings = mutableMapOf<String, MutableList<Binding>>()

    private class Binding(val holder: ITextHolder, val template: String)

    /**
     * Apply placeholder values to all text widgets in the given screen.
     *
     * @param screenId   the screen identifier (from YAML `id` field)
     * @param rootWidget the root widget of the screen's widget tree
     * @param values     resolved placeholder map, e.g. {"%player_name%" → "Steve"}
     */
    fun update(screenId: String, rootWidget: IWidget, values: Map<String, String>) {
        val holders = bindings.getOrPut(screenId) {
            mutableListOf<Binding>().also { list ->
                collectTextHolders(rootWidget, list)
            }
        }

        for (binding in holders) {
            var resolved = binding.template
            for ((key, value) in values) {
                resolved = resolved.replace(key, value)
            }
            binding.holder.text = resolved
        }
    }

    private fun collectTextHolders(component: IComponent, out: MutableList<Binding>) {
        if (component is ITextHolder && component.text.contains('%')) {
            out.add(Binding(component, component.text))
        }
        for (child in component.getChildren()) {
            collectTextHolders(child, out)
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
