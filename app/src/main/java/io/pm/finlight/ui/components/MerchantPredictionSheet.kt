// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/MerchantPredictionSheet.kt
// REASON: FIX (Crash) - Fixed "Key already used" crash in LazyColumn by making
// the item key more unique. Since the search query groups by description,
// category, and account, all three must be part of the key.
// UI REFINEMENT - Updated `PredictionItem` to display the account name as
// supporting text. This helps users distinguish between similar suggestions
// (e.g., same merchant used with different accounts) and brings it in line
// with the `PredictionCarousel` in the Add Transaction screen.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.delay
import kotlin.collections.isNotEmpty

@Composable
fun MerchantPredictionSheet(
    viewModel: TransactionViewModel,
    initialDescription: String,
    onQueryChanged: (String) -> Unit,
    onPredictionSelected: (prediction: MerchantPrediction) -> Unit,
    onManualSave: (newDescription: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentDescription by remember { mutableStateOf(initialDescription) }
    val predictions by viewModel.merchantPredictions.collectAsState()
    val focusRequester = remember { FocusRequester() }

    // Trigger search when the description text changes
    LaunchedEffect(currentDescription) {
        viewModel.onMerchantSearchQueryChanged(currentDescription)
    }

    // Request focus when the sheet appears
    LaunchedEffect(Unit) {
        delay(100) // Small delay to ensure UI is ready
        focusRequester.requestFocus()
    }

    // Clear search when the sheet is dismissed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearMerchantSearch()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(0.9f) // Take up 90% of the screen height
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            "Merchant / Description",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = currentDescription,
            onValueChange = {
                currentDescription = it
                onQueryChanged(it)
            },
            label = { Text("Search or enter new merchant") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        if (predictions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // --- FIX: Use a more unique key (description + category + account) ---
                items(predictions, key = { "${it.description}_${it.categoryId}_${it.accountId}" }) { prediction ->
                    PredictionItem(
                        prediction = prediction,
                        onClick = { onPredictionSelected(prediction) }
                    )
                }
            }
        } else if (currentDescription.length > 1) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No past transactions found matching '${currentDescription}'", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onManualSave(currentDescription) },
                enabled = currentDescription.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun PredictionItem(
    prediction: MerchantPrediction,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = prediction.description,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            // --- NEW: Display account name if available ---
            prediction.accountName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            if (prediction.categoryName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = prediction.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CategoryIconDisplay(
                        iconKey = prediction.categoryIconKey,
                        colorKey = prediction.categoryColorKey,
                        name = prediction.categoryName,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun CategoryIconDisplay(
    iconKey: String?,
    colorKey: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(CategoryIconHelper.getIconBackgroundColor(colorKey ?: "gray_light")),
        contentAlignment = Alignment.Center
    ) {
        if (name == "Uncategorized") {
            Icon(
                imageVector = CategoryIconHelper.getIcon("help_outline"),
                contentDescription = name,
                tint = Color.Black,
                modifier = Modifier.padding(4.dp)
            )
        } else if (iconKey == "letter_default") {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        } else {
            Icon(
                imageVector = CategoryIconHelper.getIcon(iconKey ?: "category"),
                contentDescription = name,
                tint = Color.Black,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}