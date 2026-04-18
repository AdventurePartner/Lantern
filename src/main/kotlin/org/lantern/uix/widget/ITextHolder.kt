package org.lantern.uix.widget

/**
 * Marker interface for widgets that hold displayable text.
 * Used by [org.lantern.internal.placeholder.PlaceholderStore] to discover
 * widgets whose text may contain PlaceholderAPI variables (e.g. `%player_name%`).
 */
interface ITextHolder {
    var text: String
}
