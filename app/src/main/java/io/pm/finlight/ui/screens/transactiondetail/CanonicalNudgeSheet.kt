package io.pm.finlight.ui.screens.transactiondetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.CanonicalNudgeSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalNudgeSheet(
    state: CanonicalNudgeSheetState,
    onToggleVariant: (String) -> Unit,
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
        CanonicalNudgeSheetContent(
            state = state,
            onToggleVariant = onToggleVariant,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Bottom sheet displayed after a rename rule is saved, surfacing historical transactions
 * from other accounts whose raw merchant name is canonically equivalent to the newly
 * saved canonical name. The user can tick each variant and apply the rename in bulk.
 */
@Composable
fun CanonicalNudgeSheetContent(
    state: CanonicalNudgeSheetState,
    onToggleVariant: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCount = state.selectedRawNames.size

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Similar merchants found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "These merchant names from other accounts look like \"${state.canonicalName}\". Apply the rename to them too?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        state.variants.forEach { variant ->
            val isSelected = variant.rawName in state.selectedRawNames
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleVariant(variant.rawName) }
                        .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleVariant(variant.rawName) },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = variant.rawName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${variant.transactionCount} transaction${if (variant.transactionCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Skip")
            }
            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (selectedCount > 0) "Apply to $selectedCount variant${if (selectedCount != 1) "s" else ""}" else "Apply",
                )
            }
        }
    }
}
