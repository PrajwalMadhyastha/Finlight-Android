// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/theme/Theme.kt
// REASON: FEATURE - The logic inside the PersonalFinanceAppTheme composable has
// been updated to fully decouple the themes from the system setting. It now
// correctly forces "Aurora" and "Midnight" to use their dark color schemes,
// and "Daybreak" and "Paper" to use their light color schemes, regardless of
// the phone's light/dark mode. "System" continues to respect the device setting.
// =================================================================================
package io.pm.finlight.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// --- Aurora Dark Theme ---
private val AuroraDarkColorScheme =
    darkColorScheme(
        primary = AuroraDarkPrimary,
        onPrimary = Color(0xFF00363A),
        secondary = AuroraDarkSecondary,
        onSecondary = Color(0xFF452B00),
        background = AuroraDarkBackground,
        onBackground = AuroraDarkOnSurface,
        surface = Color.Transparent,
        onSurface = AuroraDarkOnSurface,
        surfaceVariant = Color(0xFF3F4849),
        onSurfaceVariant = AuroraDarkOnSurfaceVariant,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

// --- Daybreak Light Theme ---
// --- UPDATED: The color scheme now uses the new, more professional Daybreak colors. ---
private val DaybreakColorScheme =
    lightColorScheme(
        primary = DaybreakPrimary,
        onPrimary = Color.White,
        secondary = DaybreakSecondary,
        onSecondary = Color(0xFF00363A), // Dark text for contrast on light blue
        background = DaybreakBackground,
        onBackground = DaybreakOnSurface,
        surface = Color.Transparent,
        onSurface = DaybreakOnSurface,
        surfaceVariant = Color(0xFFE7E0EC), // Adjusted for the new background
        onSurfaceVariant = DaybreakOnSurfaceVariant,
        error = Color(0xFFB00020),
        onError = Color.White,
    )

// --- Midnight Theme (Monochrome Dark) ---
private val MidnightColorScheme =
    darkColorScheme(
        primary = MidnightPrimary,
        onPrimary = Color.Black,
        secondary = MidnightSecondary,
        onSecondary = Color.Black,
        background = MidnightBackground,
        onBackground = MidnightOnSurface,
        surface = Color.Transparent,
        onSurface = MidnightOnSurface,
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = MidnightOnSurfaceVariant,
        error = Color(0xFFCF6679),
        onError = Color.Black,
    )

// --- Paper Theme (Monochrome Light) ---
private val PaperColorScheme =
    lightColorScheme(
        primary = PaperPrimary,
        onPrimary = Color.White,
        secondary = PaperSecondary,
        onSecondary = Color.White,
        background = PaperBackground,
        onBackground = PaperOnSurface,
        surface = Color.Transparent,
        onSurface = PaperOnSurface,
        surfaceVariant = Color(0xFFE0E0E0),
        onSurfaceVariant = PaperOnSurfaceVariant,
        error = Color(0xFFB00020),
        onError = Color.White,
    )

@Composable
fun PersonalFinanceAppTheme(
    selectedTheme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemIsDark = isSystemInDarkTheme()

    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (systemIsDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            // --- UPDATED: Decoupled theme logic ---
            else ->
                when (selectedTheme) {
                    AppTheme.SYSTEM_DEFAULT -> if (systemIsDark) MidnightColorScheme else PaperColorScheme
                    AppTheme.AURORA -> AuroraDarkColorScheme
                    AppTheme.DAYBREAK -> DaybreakColorScheme
                    AppTheme.MIDNIGHT -> MidnightColorScheme
                    AppTheme.PAPER -> PaperColorScheme
                }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
