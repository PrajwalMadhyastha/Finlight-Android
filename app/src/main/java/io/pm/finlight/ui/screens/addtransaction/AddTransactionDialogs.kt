package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import io.pm.finlight.Goal
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.CreateAccountDialog
import io.pm.finlight.ui.components.CreateCategoryDialog
import io.pm.finlight.ui.components.GoalAllocationDialog
import io.pm.finlight.ui.viewmodel.GoalViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialogs(
    showDatePicker: Boolean,
    onDismissDatePicker: () -> Unit,
    showTimePicker: Boolean,
    onDismissTimePicker: () -> Unit,
    showCreateAccountDialog: Boolean,
    onDismissCreateAccount: () -> Unit,
    showCreateCategoryDialog: Boolean,
    onDismissCreateCategory: () -> Unit,
    showGoalAllocationDialog: Long?,
    onDismissGoalAllocation: () -> Unit,
    selectedDateTime: Calendar,
    amount: Double,
    popupContainerColor: Color,
    activeGoals: List<Goal>,
    isCategoryNudgeActive: Boolean,
    viewModel: TransactionViewModel,
    goalViewModel: GoalViewModel,
    navController: NavController,
    onOpenTimePicker: () -> Unit,
) {
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTime.timeInMillis)
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance().apply { timeInMillis = it }
                        selectedDateTime.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                        selectedDateTime.set(Calendar.MONTH, cal.get(Calendar.MONTH))
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                    }
                    onDismissDatePicker()
                    onOpenTimePicker()
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
                initialHour = selectedDateTime.get(Calendar.HOUR_OF_DAY),
                initialMinute = selectedDateTime.get(Calendar.MINUTE),
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
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    selectedDateTime.set(Calendar.MINUTE, timePickerState.minute)
                    onDismissTimePicker()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismissTimePicker) { Text("Cancel") } },
        )
    }

    if (showCreateAccountDialog) {
        CreateAccountDialog(
            onDismiss = onDismissCreateAccount,
            onConfirm = { name, type, creditLimit, billingCycleDay ->
                viewModel.createAccount(name, type, creditLimit, billingCycleDay) { newAccount ->
                    viewModel.onAddTransactionAccountChanged(newAccount)
                }
                onDismissCreateAccount()
            },
        )
    }

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = onDismissCreateCategory,
            onConfirm = { name, iconKey, colorKey ->
                viewModel.createCategory(name, iconKey, colorKey) { newCategory ->
                    viewModel.onAddTransactionCategoryChanged(newCategory)
                    if (isCategoryNudgeActive) {
                        viewModel.saveWithSelectedCategory(newCategory.id) {
                            navController.popBackStack()
                        }
                    }
                }
                onDismissCreateCategory()
            },
        )
    }

    if (showGoalAllocationDialog != null) {
        GoalAllocationDialog(
            transactionAmount = amount,
            activeGoals = activeGoals,
            onDismiss = onDismissGoalAllocation,
            onConfirm = { goalsToLink ->
                goalsToLink.forEach { goal ->
                    goalViewModel.linkTransactionToGoal(goal.id, showGoalAllocationDialog.toInt())
                }
                onDismissGoalAllocation()
            },
        )
    }
}
