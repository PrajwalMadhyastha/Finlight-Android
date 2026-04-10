// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/theme/ThemeManager.kt
// REASON: FEATURE - The AppTheme enum has been updated to include "Daybreak"
// as a distinct option, separating the light and dark purple themes and giving
// the user more explicit control over the app's appearance.
// UPDATE: Swapped the icon background colors for the Midnight and Paper themes
// to better reflect their nature (dark vs. light).
// =================================================================================
package io.pm.finlight.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Enum to represent the available themes in the application.
 * This provides a type-safe way to manage theme selection.
 */
enum class AppTheme(
    val key: String,
    val displayName: String,
    val icon: ImageVector,
    val lightColor: Color,
    val darkColor: Color,
) {
    SYSTEM_DEFAULT(
        key = "system_default",
        displayName = "System",
        icon = Icons.Default.SettingsBrightness,
        lightColor = PaperPrimary,
        darkColor = MidnightPrimary,
    ),
    AURORA(
        key = "aurora",
        displayName = "Aurora",
        icon = Icons.Default.Nightlight,
        lightColor = AuroraDarkPrimary, // Fallback, dark-only theme
        darkColor = AuroraDarkPrimary,
    ),
    DAYBREAK(
        key = "daybreak",
        displayName = "Daybreak",
        icon = Icons.Default.WbSunny,
        lightColor = DaybreakPrimary,
        darkColor = DaybreakPrimary, // Fallback, light-only theme
    ),
    MIDNIGHT(
        key = "midnight",
        displayName = "Midnight",
        icon = Icons.Default.DarkMode,
        // --- FIX: Use the dark color for the dark theme's icon ---
        lightColor = PaperPrimary,
        darkColor = PaperPrimary,
    ),
    PAPER(
        key = "paper",
        displayName = "Paper",
        icon = Icons.Default.LightMode,
        // --- FIX: Use the light color for the light theme's icon ---
        lightColor = MidnightPrimary,
        darkColor = MidnightPrimary,
    ),
    ;

    companion object {
        fun fromKey(key: String?): AppTheme {
            return entries.find { it.key == key } ?: SYSTEM_DEFAULT
        }
    }
}
