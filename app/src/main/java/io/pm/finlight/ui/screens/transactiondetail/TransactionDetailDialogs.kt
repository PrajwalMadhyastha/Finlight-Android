package io.pm.finlight.ui.screens.transactiondetail

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.pm.finlight.TransactionImage
import io.pm.finlight.ui.components.ConfirmationDialog
import io.pm.finlight.ui.components.CreateAccountDialog
import io.pm.finlight.ui.components.CreateCategoryDialog
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDateTimePickerFlow(
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    initialDateTime: Calendar,
    popupContainerColor: Color,
    onDateSelected: (Calendar) -> Unit,
    onTimeSelected: (Calendar) -> Unit,
    onDismissDatePicker: () -> Unit,
    onDismissTimePicker: () -> Unit,
) {
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateTime.timeInMillis)
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val newCal = Calendar.getInstance().apply { timeInMillis = it }
                        onDateSelected(newCal)
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismissDatePicker) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = popupContainerColor.copy(alpha = 1f)),
            )
        }
    }

    if (showTimePicker) {
        val timePickerState =
            rememberTimePickerState(
                initialHour = initialDateTime.get(Calendar.HOUR_OF_DAY),
                initialMinute = initialDateTime.get(Calendar.MINUTE),
            )
        AlertDialog(
            onDismissRequest = onDismissTimePicker,
            containerColor = popupContainerColor.copy(alpha = 1f),
            title = { Text("Select Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalDateTime =
                        (initialDateTime.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                        }
                    onTimeSelected(finalDateTime)
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismissTimePicker) { Text("Cancel") } },
        )
    }
}

@Composable
fun TransactionDeleteDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return

    ConfirmationDialog(
        title = "Delete Transaction?",
        text = "Are you sure you want to permanently delete this transaction? This action cannot be undone.",
        confirmButtonText = "Delete",
        isDestructive = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
fun TransactionUnmergeDialog(
    show: Boolean,
    popupContainerColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = popupContainerColor,
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.MergeType,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Unmerge Transactions?") },
        text = {
            Text(
                "This will split the transaction back into two separate entries.\n\n" +
                    "⚠\uFE0F Any edits made to this transaction after the merge will be lost." +
                    "The original amounts, date, and notes will be restored.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Unmerge", color = MaterialTheme.colorScheme.primary)
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
fun TransactionImageViewerDialog(
    imageUri: Uri?,
    onDismiss: () -> Unit,
) {
    if (imageUri == null) return

    Dialog(onDismissRequest = onDismiss) {
        val context = LocalContext.current
        val imageRequest =
            remember(imageUri) {
                ImageRequest.Builder(context)
                    .data(imageUri)
                    .size(1920, 1920)
                    .crossfade(true)
                    .build()
            }
        AsyncImage(
            model = imageRequest,
            contentDescription = "Full screen image",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
fun TransactionImageDeleteDialog(
    image: TransactionImage?,
    onDismiss: () -> Unit,
    onConfirm: (TransactionImage) -> Unit,
) {
    if (image == null) return

    ConfirmationDialog(
        title = "Delete Attachment?",
        text = "Are you sure you want to delete this attachment? This action cannot be undone.",
        confirmButtonText = "Delete",
        isDestructive = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(image) },
    )
}

@Composable
fun TransactionCreateAccountDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, creditLimit: Double?, billingCycleDay: Int?) -> Unit,
) {
    if (!show) return
    CreateAccountDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
fun TransactionCreateCategoryDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, colorKey: String) -> Unit,
) {
    if (!show) return
    CreateCategoryDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
