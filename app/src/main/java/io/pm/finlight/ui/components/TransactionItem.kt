// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/TransactionItem.kt
// REASON: FIX - Added default values to the new selection mode parameters in
// the `TransactionItem` and `TransactionList` composables. This resolves build
// errors by making the parameters optional for screens that don't use the
// selection feature, such as the dashboard and search results.
// FEATURE - The subtitle text now displays the comma-separated list of tag names
// if they exist, falling back to the category name otherwise. This makes the
// transaction list significantly more informative at a glance.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.TransactionDetails
import io.pm.finlight.ui.theme.ExpenseRedDark
import io.pm.finlight.ui.theme.ExpenseRedLight
import io.pm.finlight.ui.theme.IncomeGreenDark
import io.pm.finlight.ui.theme.IncomeGreenLight
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    modifier: Modifier = Modifier,
    transactionDetails: TransactionDetails,
    onClick: () -> Unit,
    onCategoryClick: (TransactionDetails) -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onEnterSelectionMode: () -> Unit = {},
    onToggleSelection: () -> Unit = {}
) {
    val contentAlpha = if (transactionDetails.transaction.isExcluded) 0.5f else 1f
    val isSplit = transactionDetails.transaction.isSplit
    val isUncategorized = transactionDetails.categoryName == null || transactionDetails.categoryName == "Uncategorized"

    val clickModifier = if (isSelectionMode) {
        Modifier.clickable { onToggleSelection() }
    } else {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onEnterSelectionMode
        )
    }

    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isSplit && !isSelectionMode) { onCategoryClick(transactionDetails) }
                    .background(
                        CategoryIconHelper.getIconBackgroundColor(
                            when {
                                isSplit -> "gray_light"
                                isUncategorized -> "red_light"
                                else -> transactionDetails.categoryColorKey ?: "gray_light"
                            }
                        )
                            .copy(alpha = contentAlpha)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isSplit -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Split Transaction",
                            tint = Color.Black.copy(alpha = contentAlpha),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    isUncategorized -> {
                        Icon(
                            imageVector = CategoryIconHelper.getIcon("help_outline"),
                            contentDescription = "Uncategorized",
                            tint = Color.Black.copy(alpha = contentAlpha),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    transactionDetails.categoryIconKey == "letter_default" -> {
                        Text(
                            text = transactionDetails.categoryName?.firstOrNull()?.uppercase() ?: "?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Black.copy(alpha = contentAlpha)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = CategoryIconHelper.getIcon(transactionDetails.categoryIconKey ?: "category"),
                            contentDescription = transactionDetails.categoryName,
                            tint = Color.Black.copy(alpha = contentAlpha),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transactionDetails.transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )

                // --- UPDATED LOGIC ---
                // If tags exist, show them. Otherwise, fall back to the category name.
                val subtitleText = if (!transactionDetails.tagNames.isNullOrEmpty()) {
                    transactionDetails.tagNames
                } else if (isSplit) {
                    "Multiple Categories"
                } else {
                    transactionDetails.categoryName ?: "Uncategorized"
                }

                Text(
                    text = subtitleText!!,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.clickable(enabled = !isSplit && !isSelectionMode) { onCategoryClick(transactionDetails) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis // Handle long tag lists
                )
                // --- END OF UPDATED LOGIC ---

                Text(
                    text = SimpleDateFormat("dd MMM yy, h:mm a", Locale.getDefault()).format(Date(transactionDetails.transaction.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }

            val isIncome = transactionDetails.transaction.transactionType == "income"
            val amountColor = if (isSystemInDarkTheme()) {
                if (isIncome) IncomeGreenDark else ExpenseRedDark
            } else {
                if (isIncome) IncomeGreenLight else ExpenseRedLight
            }.copy(alpha = contentAlpha)
            val icon = if (isIncome) Icons.Default.SouthWest else Icons.Default.NorthEast

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹${"%.2f".format(transactionDetails.transaction.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = transactionDetails.transaction.transactionType,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionList(
    transactions: List<TransactionDetails>,
    navController: NavController,
    onCategoryClick: (TransactionDetails) -> Unit,
    // --- FIX: Provide default values for selection parameters ---
    isSelectionMode: Boolean = false,
    selectedIds: Set<Int> = emptySet(),
    onEnterSelectionMode: (Int) -> Unit = {},
    onToggleSelection: (Int) -> Unit = {}
) {
    if (transactions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No transactions yet. Add one!")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(transactions, key = { it.transaction.id }) { details ->
                TransactionItem(
                    modifier = Modifier.animateItemPlacement(),
                    transactionDetails = details,
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(details.transaction.id)
                        } else {
                            navController.navigate("transaction_detail/${details.transaction.id}")
                        }
                    },
                    onCategoryClick = onCategoryClick,
                    isSelectionMode = isSelectionMode,
                    isSelected = details.transaction.id in selectedIds,
                    onEnterSelectionMode = { onEnterSelectionMode(details.transaction.id) },
                    onToggleSelection = { onToggleSelection(details.transaction.id) }
                )
            }
        }
    }
}