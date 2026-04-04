package io.pm.finlight.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.pm.finlight.Category

@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit,
) {
    var updatedName by remember {
        mutableStateOf(TextFieldValue(category.name, TextRange(category.name.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = {
            OutlinedTextField(
                value = updatedName,
                onValueChange = { updatedName = it },
                label = { Text("Category Name") },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (updatedName.text.isNotBlank()) {
                        onConfirm(category.copy(name = updatedName.text))
                    }
                },
                enabled = updatedName.text.isNotBlank(),
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DeleteCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category") },
        text = { Text("Are you sure you want to delete the category '${category.name}'? This cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
