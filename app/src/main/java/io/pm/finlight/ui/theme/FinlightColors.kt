package io.pm.finlight.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Domain-specific semantic financial color tokens for Finlight design system.
 */
data class FinlightColors(
    val income: Color,
    val expense: Color,
    val warning: Color,
    val success: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
)

val DarkFinlightColors =
    FinlightColors(
        income = IncomeGreenDark,
        expense = ExpenseRedDark,
        warning = Color(0xFFFF8C00),
        success = Color(0xFF34C759),
        glassSurface = Color.White.copy(alpha = 0.08f),
        glassBorder = GlassPanelBorder,
        glassHighlight = AuroraNumpadHighlight,
    )

val LightFinlightColors =
    FinlightColors(
        income = IncomeGreenLight,
        expense = ExpenseRedLight,
        warning = Color(0xFFFF8C00),
        success = Color(0xFF34C759),
        glassSurface = Color.Black.copy(alpha = 0.04f),
        // Translucent dark glass border (~15% black)
        glassBorder = Color(0x26000000),
        glassHighlight = Color.Black.copy(alpha = 0.08f),
    )

/**
 * Resolves the appropriate [FinlightColors] based on the selected theme, system dark mode setting,
 * and dynamic color preference.
 */
fun resolveFinlightColors(
    selectedTheme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    isSystemDark: Boolean,
    dynamicColor: Boolean = false,
): FinlightColors =
    when {
        dynamicColor -> if (isSystemDark) DarkFinlightColors else LightFinlightColors
        selectedTheme == AppTheme.AURORA -> DarkFinlightColors
        selectedTheme == AppTheme.MIDNIGHT -> DarkFinlightColors
        selectedTheme == AppTheme.DAYBREAK -> LightFinlightColors
        selectedTheme == AppTheme.PAPER -> LightFinlightColors
        selectedTheme == AppTheme.SYSTEM_DEFAULT -> if (isSystemDark) DarkFinlightColors else LightFinlightColors
        else -> if (isSystemDark) DarkFinlightColors else LightFinlightColors
    }

val LocalFinlightColors =
    staticCompositionLocalOf<FinlightColors> {
        DarkFinlightColors
    }

object FinlightTheme {
    val colors: FinlightColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFinlightColors.current
}
