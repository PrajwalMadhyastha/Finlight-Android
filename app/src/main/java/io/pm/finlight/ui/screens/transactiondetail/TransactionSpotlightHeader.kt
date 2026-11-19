package io.pm.finlight.ui.screens.transactiondetail

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pm.finlight.Category
import io.pm.finlight.R
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.components.CategoryIconDisplay
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.rememberSafeDrawablePainter
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.FormatUtils
import java.util.Date
import kotlin.math.abs

@Composable
fun TransactionSpotlightHeader(
    details: TransactionDetails,
    displayDate: Date,
    visitCount: Int,
    isSplit: Boolean,
    hasMerged: Boolean = false,
    hasReimbursements: Boolean = false,
    onDescriptionClick: () -> Unit,
    onAmountClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDateTimeClick: () -> Unit,
    onSplitClick: () -> Unit,
    onVisitCountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayCategory =
        if (isSplit) {
            Category(name = "Multiple Categories", iconKey = "call_split", colorKey = "gray_light")
        } else {
            details.toCategory()
        }

    val headerDescription = if (isSplit) "Split Transaction" else details.transaction.description
    val categoryColor = CategoryIconHelper.getIconBackgroundColor(displayCategory.colorKey)
    val dateFormatter = remember { FormatUtils.fullDateTimeFormatter }

    val animatedAmount by animateFloatAsState(
        targetValue = abs(details.transaction.amount).toFloat(),
        animationSpec = tween(1500, easing = EaseOutCubic),
        label = "AmountAnimation",
    )

    GlassPanel(
        modifier =
            modifier
                .fillMaxWidth()
                .height(350.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val categoryBgRes = CategoryIconHelper.getCategoryBackground(displayCategory.iconKey)
            val isPredefined = categoryBgRes != R.drawable.bg_cat_general
            if (isPredefined && !isSplit) {
                Image(
                    painter =
                        rememberSafeDrawablePainter(
                            resId = categoryBgRes,
                            fallbackResId = R.drawable.bg_cat_general,
                        ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    alpha = 0.3f,
                )
            } else {
                DynamicCategoryBackground(category = displayCategory, isSplit = isSplit)
            }

            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.6f)),
                            ),
                        ),
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                drawIntoCanvas {
                    val paint = Paint().asFrameworkPaint()
                    val radius = size.width * 0.8f
                    paint.color = AndroidColor.TRANSPARENT
                    paint.setShadowLayer(
                        radius,
                        0f,
                        0f,
                        categoryColor
                            .copy(alpha = 0.4f)
                            .toArgb(),
                    )
                    it.drawCircle(center, radius / 2, Paint().apply { this.color = Color.Transparent })
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = headerDescription,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .clickable(onClick = onDescriptionClick)
                                .padding(horizontal = 16.dp),
                    )
                    if (details.transaction.isSplit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Split Transaction",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = "₹${"%,.2f".format(animatedAmount)}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clickable(onClick = onAmountClick),
                )
                Spacer(Modifier.height(16.dp))
                if (!isSplit) {
                    ChipWithIcon(
                        text = displayCategory.name,
                        onClick = onCategoryClick,
                        category = displayCategory,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (!hasMerged && !hasReimbursements) {
                    OutlinedButton(
                        onClick = onSplitClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    ) {
                        val icon = if (isSplit) Icons.Default.Edit else Icons.AutoMirrored.Filled.CallSplit
                        val text = if (isSplit) "Edit Splits" else "Split Transaction"
                        val contentDesc = if (isSplit) "Edit" else "Split Transaction"
                        Icon(icon, contentDescription = contentDesc, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dateFormatter.format(displayDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.clickable(onClick = onDateTimeClick),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Transaction Source",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = details.transaction.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            if (visitCount > 1) {
                val chipLabel =
                    if (details.transaction.transactionType == TransactionType.INCOME) {
                        "$visitCount credits"
                    } else {
                        "$visitCount visits"
                    }
                AssistChip(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    onClick = onVisitCountClick,
                    label = { Text(chipLabel) },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

@Composable
fun DynamicCategoryBackground(
    category: Category,
    isSplit: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = CategoryIconHelper.getIconBackgroundColor(category.colorKey)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (isSplit) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = "Split Transaction Background",
                modifier = Modifier.size(250.dp),
                tint = color.copy(alpha = 0.15f),
            )
        } else {
            val letter = if (category.name == "Uncategorized") "?" else category.name.firstOrNull()?.uppercase() ?: "?"
            Text(
                text = letter,
                fontSize = 250.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.15f),
            )
        }
    }
}

@Composable
fun ChipWithIcon(
    text: String,
    onClick: () -> Unit,
    category: Category,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .background(
                    CategoryIconHelper
                        .getIconBackgroundColor(category.colorKey)
                        .copy(alpha = 0.9f),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryIconDisplay(
            category = category,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ReviewBannerCard(
    reason: String,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Needs Review",
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Needs Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = onReviewClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Mark as Reviewed")
            }
        }
    }
}

fun TransactionDetails.toCategory(): Category {
    return if (this.categoryName == null || this.categoryName == "Uncategorized") {
        Category(
            id = 0,
            name = "Uncategorized",
            iconKey = "help_outline",
            colorKey = "red_light",
        )
    } else {
        Category(
            id = this.transaction.categoryId ?: 0,
            name = this.categoryName,
            iconKey = this.categoryIconKey ?: "category",
            colorKey = this.categoryColorKey ?: "gray_light",
        )
    }
}
