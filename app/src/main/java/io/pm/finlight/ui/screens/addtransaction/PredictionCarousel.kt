package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.utils.CategoryIconHelper

@Composable
fun PredictionCarousel(
    predictions: List<MerchantPrediction>,
    onPredictionSelected: (MerchantPrediction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Past Merchants",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(predictions, key = { it.uniqueKey }) { prediction ->
                PredictionChip(
                    prediction = prediction,
                    onClick = { onPredictionSelected(prediction) },
                )
            }
        }
    }
}

@Composable
fun PredictionChip(
    prediction: MerchantPrediction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = CategoryIconHelper.getIcon(prediction.categoryIconKey ?: "category")
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = prediction.description,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (prediction.categoryName != null || prediction.accountName != null) {
                    val subtitle =
                        buildString {
                            if (prediction.categoryName != null) append(prediction.categoryName)
                            if (prediction.accountName != null) {
                                if (isNotEmpty()) append(" • ")
                                append(prediction.accountName)
                            }
                        }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
