// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/CreationDialogs.kt
// REASON: NEW FILE - Centralized the creation dialogs to resolve conflicting
// overload errors and improve code organization.
// BUG FIX - The AlertDialogs now correctly derive their background color from
// the app's MaterialTheme, ensuring they match the selected theme (e.g.,
// Aurora) instead of defaulting to the system's light/dark mode.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.pm.finlight.AccountType
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.CategoryIconHelper

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
private fun getPopupContainerColor() = if (MaterialTheme.colorScheme.background.isDark()) PopupSurfaceDark else PopupSurfaceLight

@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, creditLimit: Double?, billingCycleDay: Int?) -> Unit,
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var type by remember { mutableStateOf(TextFieldValue("")) }
    var creditLimitText by remember { mutableStateOf(TextFieldValue("")) }
    var billingDayText by remember { mutableStateOf(TextFieldValue("")) }

    val isCreditCard = AccountType.fromString(type.text) == AccountType.CREDIT_CARD
    val isCreditLimitInvalid =
        isCreditCard && creditLimitText.text.isNotBlank() &&
            (creditLimitText.text.toDoubleOrNull() == null || (creditLimitText.text.toDoubleOrNull() ?: 0.0) <= 0.0)
    val billingDayInt = billingDayText.text.toIntOrNull()
    val isBillingDayInvalid =
        isCreditCard && billingDayText.text.isNotBlank() &&
            (billingDayInt == null || billingDayInt !in 1..31)

    val popupContainerColor = getPopupContainerColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name (e.g., Chase Sapphire)") },
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Account Type (e.g., Credit Card)") },
                )
                AnimatedVisibility(visible = isCreditCard) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = creditLimitText,
                            onValueChange = { newValue ->
                                if (newValue.text.all { it.isDigit() || it == '.' }) {
                                    creditLimitText = newValue
                                }
                            },
                            label = { Text("Credit Limit (e.g., 100000)") },
                            isError = isCreditLimitInvalid,
                            supportingText =
                                if (isCreditLimitInvalid) {
                                    { Text("Please enter a valid credit limit") }
                                } else {
                                    null
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = billingDayText,
                            onValueChange = { newValue ->
                                if (newValue.text.all { it.isDigit() } && newValue.text.length <= 2) {
                                    billingDayText = newValue
                                }
                            },
                            label = { Text("Billing Day (1 - 31)") },
                            isError = isBillingDayInvalid,
                            supportingText =
                                if (isBillingDayInvalid) {
                                    { Text("Day must be between 1 and 31") }
                                } else {
                                    null
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedLimit = if (isCreditCard) creditLimitText.text.toDoubleOrNull() else null
                    val parsedBillingDay = if (isCreditCard) billingDayText.text.toIntOrNull()?.coerceIn(1, 31) else null
                    onConfirm(name.text, type.text, parsedLimit, parsedBillingDay)
                },
                enabled = name.text.isNotBlank() && type.text.isNotBlank() && !isCreditLimitInvalid && !isBillingDayInvalid,
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = popupContainerColor,
    )
}

@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    CreateAccountDialog(
        onDismiss = onDismiss,
        onConfirm = { name, type, _, _ -> onConfirm(name, type) },
    )
}

@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var selectedIconKey by remember { mutableStateOf("category") }
    var selectedColorKey by remember { mutableStateOf("gray_light") }
    val allIcons = remember { CategoryIconHelper.getAllIcons().entries.toList() }
    val allColors = remember { CategoryIconHelper.getAllIconColors().entries.toList() }

    val popupContainerColor = getPopupContainerColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Select Icon", style = MaterialTheme.typography.titleMedium)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier.heightIn(max = 150.dp),
                ) {
                    items(allIcons) { (key, icon) ->
                        IconButton(
                            onClick = { selectedIconKey = key },
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (selectedIconKey == key) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = MaterialTheme.shapes.medium,
                                    ),
                        ) {
                            Icon(imageVector = icon, contentDescription = key)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Select Color", style = MaterialTheme.typography.titleMedium)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier.heightIn(max = 100.dp),
                ) {
                    items(allColors) { (key, color) ->
                        Box(
                            modifier =
                                Modifier
                                    .padding(6.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColorKey = key }
                                    .border(
                                        width = 2.dp,
                                        color = if (selectedColorKey == key) MaterialTheme.colorScheme.outline else Color.Transparent,
                                        shape = CircleShape,
                                    ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.text, selectedIconKey, selectedColorKey) },
                enabled = name.text.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = popupContainerColor,
    )
}
