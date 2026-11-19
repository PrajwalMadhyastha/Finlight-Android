package io.pm.finlight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.pm.finlight.ui.theme.FinlightTheme
import kotlin.math.roundToInt

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    isCustomizationMode: Boolean = false,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val finalBorderColor = borderColor ?: FinlightTheme.colors.glassBorder

    val borderModifier =
        if (isCustomizationMode) {
            Modifier.border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(finalBorderColor, finalBorderColor.copy(alpha = 0.5f))),
                shape = RoundedCornerShape(24.dp),
            )
        } else {
            Modifier.border(1.dp, finalBorderColor, RoundedCornerShape(24.dp))
        }

    val defaultFillColor = FinlightTheme.colors.glassSurface

    val glassFillColor = backgroundColor ?: defaultFillColor

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(glassFillColor)
                .then(borderModifier),
        content = content,
    )
}

@Composable
fun AuroraProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedPercentage = (progress * 100).roundToInt()
    val clampedProgress = progress.coerceIn(0f, 1f)
    val colors = FinlightTheme.colors

    // Vibrant gradient colors - adjusted warning to be more orange
    val progressGradient =
        when {
            progress > 0.9 -> listOf(colors.expense, Color(0xFFFF2D55))
            progress > 0.7 -> listOf(colors.warning, Color(0xFFFF9500))
            else -> listOf(colors.success, Color(0xFF30D158))
        }

    val borderColor = colors.glassBorder

    // Zone colors for background - subtle but visible
    val greenZone = colors.success.copy(alpha = 0.20f)
    val orangeZone = colors.warning.copy(alpha = 0.20f)
    val redZone = colors.expense.copy(alpha = 0.20f)

    Layout(
        modifier =
            modifier.semantics {
                this.progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
        content = {
            Text(
                text = "$animatedPercentage%",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp),
            ) {
                // Border
                drawRoundRect(
                    color = borderColor,
                    size = size,
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Green zone (0-70%)
                drawRoundRect(
                    color = greenZone,
                    size = Size(width = size.width * 0.7f, height = size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )

                // Orange zone (70-90%)
                drawRect(
                    color = orangeZone,
                    topLeft = Offset(size.width * 0.7f, 0f),
                    size = Size(width = size.width * 0.2f, height = size.height),
                )

                // Red zone (90-100%)
                drawRoundRect(
                    color = redZone,
                    topLeft = Offset(size.width * 0.9f, 0f),
                    size = Size(width = size.width * 0.1f, height = size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )

                if (clampedProgress > 0) {
                    // Vibrant progress fill
                    drawRoundRect(
                        brush = Brush.horizontalGradient(colors = progressGradient),
                        size = Size(width = size.width * clampedProgress, height = size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )

                    // Inner glow
                    drawRoundRect(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = 0.3f),
                                        Color.White.copy(alpha = 0.1f),
                                    ),
                            ),
                        size = Size(width = size.width * clampedProgress, height = size.height * 0.5f),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(Constraints())
        val canvasPlaceable = measurables[1].measure(constraints)
        val progressWidth = (canvasPlaceable.width * clampedProgress).toInt()
        val textX = (progressWidth - textPlaceable.width / 2).coerceIn(0, canvasPlaceable.width - textPlaceable.width)
        layout(canvasPlaceable.width, canvasPlaceable.height + textPlaceable.height + 4.dp.roundToPx()) {
            canvasPlaceable.placeRelative(0, textPlaceable.height + 4.dp.roundToPx())
            textPlaceable.placeRelative(textX, 0)
        }
    }
}
