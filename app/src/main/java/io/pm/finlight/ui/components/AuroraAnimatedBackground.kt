package io.pm.finlight.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

internal val AuroraCircles: List<AnimatedCircle> =
    listOf(
        AnimatedCircle(
            color = Color(0xFF8A2BE2),
            baseRadiusMultiplier = 0.8f,
            baseCenter = Offset(0.2f, 0.2f),
            speedMultiplier = 0.6f,
            alpha = 0.3f,
        ),
        AnimatedCircle(
            color = Color(0xFF00008B),
            baseRadiusMultiplier = 0.9f,
            baseCenter = Offset(0.8f, 0.3f),
            speedMultiplier = 0.4f,
            alpha = 0.3f,
        ),
        AnimatedCircle(
            color = Color(0xFF008080),
            baseRadiusMultiplier = 1.0f,
            baseCenter = Offset(0.4f, 0.8f),
            speedMultiplier = 0.5f,
            alpha = 0.3f,
        ),
        AnimatedCircle(
            color = Color(0xFF4B0082),
            baseRadiusMultiplier = 0.7f,
            baseCenter = Offset(0.9f, 0.9f),
            speedMultiplier = 0.7f,
            alpha = 0.3f,
        ),
    )

/**
 * A composable that renders a slow, gently moving aurora-like background effect.
 * It uses an infinite transition to animate the properties of several large,
 * blurred, overlapping circles.
 */
@Composable
fun AuroraAnimatedBackground(
    modifier: Modifier = Modifier,
    animationsDisabled: Boolean? = null,
) {
    AnimatedGradientBackground(
        circles = AuroraCircles,
        durationMillis = 30000,
        label = "Aurora",
        modifier = modifier,
        animationsDisabled = animationsDisabled,
    )
}
