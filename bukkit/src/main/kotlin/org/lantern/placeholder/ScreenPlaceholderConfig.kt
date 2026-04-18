package org.lantern.placeholder

/**
 * Holds the PlaceholderAPI configuration for a single screen.
 *
 * @param screenId  The screen id as defined in the YAML (e.g. "my_hud").
 * @param placeholders  The raw placeholder keys to resolve (e.g. ["%player_name%", "%player_health%"]).
 * @param intervalTicks  How often (in server ticks) to resolve and push updates.
 */
data class ScreenPlaceholderConfig(
    val screenId: String,
    val placeholders: List<String>,
    val intervalTicks: Long
)
