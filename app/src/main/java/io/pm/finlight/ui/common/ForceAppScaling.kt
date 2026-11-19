package io.pm.finlight.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

internal const val MIN_WIDTH_DP = 375f

/**
 * A wrapper that enforces a consistent app scaling experience, preserving system-level
 * accessibility font scaling while clamping display zoom if it compromises layout integrity.
 *
 * BEHAVIOR:
 * 1. Preserves user-configured system accessibility [Density.fontScale].
 * 2. Clamps [Density.density] (DPI) if the screen width drops below 375dp (e.g., due to Display Zoom),
 * ensuring the layout never gets "crunched" narrower than a standard small phone.
 */
@Composable
fun ForceAppScaling(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthDp = configuration.screenWidthDp.toFloat()

    val densityMultiplier =
        if (screenWidthDp < MIN_WIDTH_DP && screenWidthDp > 0) {
            screenWidthDp / MIN_WIDTH_DP
        } else {
            1f
        }

    val customDensity =
        Density(
            density = density.density * densityMultiplier,
            fontScale = density.fontScale,
        )

    CompositionLocalProvider(
        LocalDensity provides customDensity,
        content = content,
    )
}
