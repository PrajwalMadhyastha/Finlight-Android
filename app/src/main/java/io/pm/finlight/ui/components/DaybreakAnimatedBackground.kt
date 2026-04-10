package io.pm.finlight.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

/**
 * A composable that renders a slow, gently moving daybreak-like background effect.
 * It uses an infinite transition to animate the properties of several large,
 * blurred, overlapping circles with light, sunrise colors.
 */
@Composable
fun DaybreakAnimatedBackground() {
    val transition = rememberInfiniteTransition(label = "DaybreakBackgroundTransition")

    // Animate the sine wave over time to create a gentle vertical bobbing motion.
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 35000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "DaybreakTime",
    )

    // --- UPDATED: Replaced vibrant colors with a softer, more professional palette ---
    val circles =
        listOf(
            AnimatedCircle(
                color = Color(0xFFD0BCFF), // Soft Lavender
                baseRadiusMultiplier = 0.8f,
                baseCenter = Offset(0.2f, 0.8f),
                speedMultiplier = 0.6f,
            ),
            AnimatedCircle(
                color = Color(0xFFCCC2DC), // Muted Purple
                baseRadiusMultiplier = 0.9f,
                baseCenter = Offset(0.8f, 0.7f),
                speedMultiplier = 0.4f,
            ),
            AnimatedCircle(
                color = Color(0xFFB9A2DB), // A slightly deeper purple
                baseRadiusMultiplier = 1.0f,
                baseCenter = Offset(0.6f, 0.2f),
                speedMultiplier = 0.5f,
            ),
            AnimatedCircle(
                color = Color(0xFF90CAF9), // Soft Blue
                baseRadiusMultiplier = 0.7f,
                baseCenter = Offset(0.9f, 0.1f),
                speedMultiplier = 0.7f,
            ),
        )

    Canvas(modifier = Modifier.fillMaxSize()) {
        circles.forEach { circle ->
            // Calculate the vertical offset using a sine wave for a smooth bobbing effect.
            val yOffset = sin(time * circle.speedMultiplier) * size.height * 0.1f

            // Create a radial gradient brush to simulate a blurred, glowing effect.
            // --- UPDATED: Reduced alpha for a more subtle effect ---
            val brush =
                Brush.radialGradient(
                    colors = listOf(circle.color.copy(alpha = 0.4f), Color.Transparent),
                    center =
                        Offset(
                            x = circle.baseCenter.x * size.width,
                            y = (circle.baseCenter.y * size.height) + yOffset,
                        ),
                    radius = size.maxDimension * circle.baseRadiusMultiplier,
                )

            // Draw the circle onto the canvas.
            drawCircle(
                brush = brush,
                radius = size.maxDimension * circle.baseRadiusMultiplier,
                center =
                    Offset(
                        x = circle.baseCenter.x * size.width,
                        y = (circle.baseCenter.y * size.height) + yOffset,
                    ),
            )
        }
    }
}
