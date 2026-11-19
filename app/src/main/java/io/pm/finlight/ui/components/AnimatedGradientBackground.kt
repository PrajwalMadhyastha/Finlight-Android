package io.pm.finlight.ui.components

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sin

/**
 * Precomputed geometry and brush for drawing an animated circle without per-frame allocations.
 */
internal class CachedCircle(
    val brush: Brush,
    val center: Offset,
    val radius: Float,
    val speedMultiplier: Float,
)

/**
 * Pre-computes cached circle geometry and radial gradient brushes whenever canvas dimensions change.
 */
internal fun createCachedCircles(
    circles: List<AnimatedCircle>,
    size: Size,
): Array<CachedCircle> {
    if (size.width <= 0f || size.height <= 0f) {
        return emptyArray()
    }
    val maxDim = size.maxDimension
    return Array(circles.size) { index ->
        val circle = circles[index]
        val center =
            Offset(
                x = circle.baseCenter.x * size.width,
                y = circle.baseCenter.y * size.height,
            )
        val radius = maxDim * circle.baseRadiusMultiplier
        val brush =
            Brush.radialGradient(
                colors = circle.gradientColors,
                center = center,
                radius = radius,
            )
        CachedCircle(
            brush = brush,
            center = center,
            radius = radius,
            speedMultiplier = circle.speedMultiplier,
        )
    }
}

/**
 * Checks whether system animations are disabled or device is under power-saving mode.
 */
internal fun isAnimationDisabled(context: Context): Boolean {
    val durationScale =
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
        } catch (_: Exception) {
            1.0f
        }
    val powerManager =
        try {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (_: Exception) {
            null
        }
    val isPowerSave =
        try {
            powerManager?.isPowerSaveMode ?: false
        } catch (_: Exception) {
            false
        }
    return durationScale == 0.0f || isPowerSave
}

/**
 * Common high-performance composable for rendering soft, glowing animated gradient backgrounds.
 * Uses `Modifier.drawWithCache` and Canvas translations to ensure zero heap allocations during
 * animation frames. Renders a static gradient without transitions when animations are disabled.
 */
@Composable
internal fun AnimatedGradientBackground(
    circles: List<AnimatedCircle>,
    durationMillis: Int,
    label: String,
    modifier: Modifier = Modifier,
    animationsDisabled: Boolean? = null,
) {
    val context = LocalContext.current
    val disabled = animationsDisabled ?: remember(context) { isAnimationDisabled(context) }
    if (disabled) {
        Spacer(
            modifier =
                modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val cachedCircles = createCachedCircles(circles, size)
                        onDrawBehind {
                            for (i in cachedCircles.indices) {
                                val circle = cachedCircles[i]
                                drawCircle(
                                    brush = circle.brush,
                                    radius = circle.radius,
                                    center = circle.center,
                                )
                            }
                        }
                    },
        )
    } else {
        val transition = rememberInfiniteTransition(label = "${label}BackgroundTransition")
        val timeState =
            transition.animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * Math.PI).toFloat(),
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "${label}Time",
            )

        Spacer(
            modifier =
                modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val cachedCircles = createCachedCircles(circles, size)
                        onDrawBehind {
                            val currentTime = timeState.value
                            val height = size.height
                            for (i in cachedCircles.indices) {
                                val circle = cachedCircles[i]
                                val yOffset = sin(currentTime * circle.speedMultiplier) * height * 0.1f
                                translate(left = 0f, top = yOffset) {
                                    drawCircle(
                                        brush = circle.brush,
                                        radius = circle.radius,
                                        center = circle.center,
                                    )
                                }
                            }
                        }
                    },
        )
    }
}
