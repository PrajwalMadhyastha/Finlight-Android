// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/theme/Color.kt
// REASON: UX REFINEMENT - The opacity of the popup surfaces has been increased
// from 85% (0xD9) to 95% (0xF2). This makes popups and bottom sheets more
// solid, reducing distraction from the background and improving overall
// legibility on both light and dark themes.
// =================================================================================
package io.pm.finlight.ui.theme

import androidx.compose.ui.graphics.Color

// --- Aurora Theme (Dark Purple) ---
val AuroraDarkPrimary = Color(0xFF00E5FF) // Vibrant Teal/Cyan
val AuroraDarkSecondary = Color(0xFFFFBA3F) // Subtle Gold
val AuroraDarkBackground = Color(0xFF121212) // Very dark charcoal
val AuroraDarkOnSurface = Color(0xFFE6E1E5) // Off-white for text
val AuroraDarkOnSurfaceVariant = Color(0xFFC4C7C5) // Brighter gray for secondary text

// --- Daybreak Theme (Light Purple) ---
val DaybreakPrimary = Color(0xFF6750A4) // Muted, professional purple
val DaybreakSecondary = Color(0xFF81D4FA) // Soft, light blue for secondary accents
val DaybreakBackground = Color(0xFFEADDFF) // Light lavender background
val DaybreakOnSurface = Color(0xFF1D1B20) // Dark, readable text
val DaybreakOnSurfaceVariant = Color(0xFF49454F) // Softer gray for secondary text

// --- Midnight Theme (Monochrome Dark) ---
val MidnightPrimary = Color(0xFFBBBBBB) // A bright, neutral gray for accents
val MidnightSecondary = Color(0xFF888888) // A softer gray
val MidnightBackground = Color(0xFF000000) // Pure black for deep contrast
val MidnightOnSurface = Color(0xFFFFFFFF) // Pure white for maximum readability
val MidnightOnSurfaceVariant = Color(0xFFB0B0B0) // Lighter gray for secondary text

// --- Paper Theme (Monochrome Light/Creme) ---
val PaperPrimary = Color(0xFF444444) // A strong, dark gray for accents
val PaperSecondary = Color(0xFF757575) // A medium gray
val PaperBackground = Color(0xFFFDFCF7) // A soft, off-white/creme
val PaperOnSurface = Color(0xFF1C1C1C) // Near-black for maximum readability
val PaperOnSurfaceVariant = Color(0xFF555555) // Dark gray for secondary text

// --- Shared Colors ---
val GlassPanelBorder = Color(0x33FFFFFF) // White with 20% opacity
val AuroraNumpadHighlight = Color(0x29FFFFFF) // White with 16% opacity

// --- UPDATED: Increased opacity from 85% (0xD9) to 95% (0xF2) ---
val PopupSurfaceDark = Color(0xF2212125) // 95% opaque dark charcoal
val PopupSurfaceLight = Color(0xF2FFFFFF) // 95% opaque white

// High-contrast colors for transaction amounts
val IncomeGreenDark = Color(0xFF66BB6A)
val ExpenseRedDark = Color(0xFFEF5350)
val IncomeGreenLight = Color(0xFF2E7D32)
val ExpenseRedLight = Color(0xFFC62828)
