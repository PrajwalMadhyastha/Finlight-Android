package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

@Composable
fun SpotlightBackground(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (color == Color.Transparent) 0f else 0.3f,
        animationSpec = tween(500),
        label = "SpotlightAlpha",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas {
            val paint = Paint().asFrameworkPaint()
            val radius = size.width * 1.2f
            paint.color = android.graphics.Color.TRANSPARENT
            paint.setShadowLayer(
                radius,
                0f,
                0f,
                color
                    .copy(alpha = animatedAlpha)
                    .toArgb(),
            )
            it.nativeCanvas.drawCircle(center.x, center.y, radius / 2, paint)
        }
    }
}
