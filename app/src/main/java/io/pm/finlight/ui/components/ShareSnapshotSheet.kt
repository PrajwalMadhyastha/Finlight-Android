package io.pm.finlight.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * An enum representing the fields that can be included in a shared transaction snapshot.
 */
enum class ShareableField(val displayName: String) {
    Date("Date"),
    Description("Description"),
    Amount("Amount"),
    Category("Category"),
    Tags("Tags")
}

@Composable
fun ShareSnapshotSheet(
    selectedFields: Set<ShareableField>,
    onFieldToggle: (ShareableField) -> Unit,
    onGenerateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val allFields = ShareableField.entries

    // The root Column now fills the entire height of the bottom sheet.
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            "Customize Your Snapshot",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Select the fields you want to include in the shared image.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // The LazyColumn now takes up all available vertical space.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allFields) { field ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFieldToggle(field) }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = field in selectedFields,
                        onCheckedChange = { onFieldToggle(field) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            // --- FIX: Use onPrimary for the checkmark color, not surface ---
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    Text(
                        text = field.displayName,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onGenerateClick,
                modifier = Modifier.weight(1f),
                enabled = selectedFields.isNotEmpty()
            ) {
                Text("Generate Image")
            }
        }
    }
}
