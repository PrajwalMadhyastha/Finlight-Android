package io.pm.finlight.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

internal val DaybreakCircles: List<AnimatedCircle> =
    listOf(
        AnimatedCircle(
            color = Color(0xFFD0BCFF),
            baseRadiusMultiplier = 0.8f,
            baseCenter = Offset(0.2f, 0.8f),
            speedMultiplier = 0.6f,
            alpha = 0.4f,
        ),
        AnimatedCircle(
            color = Color(0xFFCCC2DC),
            baseRadiusMultiplier = 0.9f,
            baseCenter = Offset(0.8f, 0.7f),
            speedMultiplier = 0.4f,
            alpha = 0.4f,
        ),
        AnimatedCircle(
            color = Color(0xFFB9A2DB),
            baseRadiusMultiplier = 1.0f,
            baseCenter = Offset(0.6f, 0.2f),
            speedMultiplier = 0.5f,
            alpha = 0.4f,
        ),
        AnimatedCircle(
            color = Color(0xFF90CAF9),
            baseRadiusMultiplier = 0.7f,
            baseCenter = Offset(0.9f, 0.1f),
            speedMultiplier = 0.7f,
            alpha = 0.4f,
        ),
    )

/**
 * A composable that renders a slow, gently moving daybreak-like background effect.
 * It uses an infinite transition to animate the properties of several large,
 * blurred, overlapping circles with light, sunrise colors.
 */
@Composable
fun DaybreakAnimatedBackground(
    modifier: Modifier = Modifier,
    animationsDisabled: Boolean? = null,
) {
    AnimatedGradientBackground(
        circles = DaybreakCircles,
        durationMillis = 35000,
        label = "Daybreak",
        modifier = modifier,
        animationsDisabled = animationsDisabled,
    )
}
