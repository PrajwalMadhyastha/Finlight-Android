package io.pm.finlight.ui.screens.transactiondetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.pm.finlight.RetroUpdateSheetState
import io.pm.finlight.Transaction
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.utils.FormatUtils
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetrospectiveUpdateSheet(
    state: RetroUpdateSheetState,
    onToggleSelection: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleUpdateFuture: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        containerColor = containerColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
    ) {
        RetrospectiveUpdateSheetContent(
            state = state,
            onToggleSelection = onToggleSelection,
            onToggleSelectAll = onToggleSelectAll,
            onToggleUpdateFuture = onToggleUpdateFuture,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun RetrospectiveUpdateSheetContent(
    state: RetroUpdateSheetState,
    onToggleSelection: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleUpdateFuture: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val changeType = if (state.newDescription != null) "name" else "category"

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            "Smart Update",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "You changed the $changeType for '${state.originalDescription}'. How should this apply?",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleUpdateFuture)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Update future transactions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Teach the app to auto-apply this change to new incoming SMS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.updateFutureTransactions,
                    onCheckedChange = { onToggleUpdateFuture() },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.similarTransactions.isNotEmpty()) {
            Text(
                "Also update ${state.similarTransactions.size} similar past transaction(s):",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleSelectAll),
            ) {
                val allSelected = state.selectedIds.size == state.similarTransactions.size
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = null,
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.similarTransactions, key = { it.id }) { transaction ->
                    SelectableTransactionItem(
                        transaction = transaction,
                        isSelected = transaction.id in state.selectedIds,
                        onToggle = { onToggleSelection(transaction.id) },
                    )
                }
            }
        } else {
            Text(
                "No earlier similar transactions to update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = state.updateFutureTransactions || state.selectedIds.isNotEmpty(),
            ) {
                Text("Apply Changes")
            }
        }
        Text(
            text = "Cancelling applies the edit only to this transaction — past transactions and future SMS will not be affected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        )
    }
}

@Composable
fun SelectableTransactionItem(
    transaction: Transaction,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { FormatUtils.shortYearDateFormatter }

    GlassPanel(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateFormatter.format(Date(transaction.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "₹${"%,.2f".format(transaction.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
