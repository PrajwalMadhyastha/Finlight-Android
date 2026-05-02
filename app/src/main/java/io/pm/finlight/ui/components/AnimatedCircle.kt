package io.pm.finlight.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Data class to hold the properties of each animated circle for background effects.
 * This is marked as 'internal' to be accessible within the 'app' module.
 *
 * @param color The base color of the circle.
 * @param baseRadiusMultiplier The radius of the circle as a multiplier of the canvas's max dimension.
 * @param baseCenter The center of the circle as a fraction of the canvas's width and height.
 * @param speedMultiplier A multiplier to vary the animation speed of each circle.
 */
internal data class AnimatedCircle(
    val color: Color,
    val baseRadiusMultiplier: Float,
    val baseCenter: Offset,
    val speedMultiplier: Float,
)
