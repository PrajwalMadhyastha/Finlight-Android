package io.pm.finlight.ui.screens.transactiondetail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.InlineTextToolbarActionBar
import io.pm.finlight.ui.components.MerchantPredictionSheet
import io.pm.finlight.ui.components.pickers.AccountPickerBottomSheet
import io.pm.finlight.ui.components.pickers.CategoryPickerBottomSheet
import io.pm.finlight.ui.components.pickers.TagPickerBottomSheet
import io.pm.finlight.ui.components.rememberInlineTextToolbar
import io.pm.finlight.ui.viewmodel.AccountViewModel
import kotlinx.coroutines.delay

sealed class SheetContent {
    object Amount : SheetContent()

    object Notes : SheetContent()

    object Account : SheetContent()

    object Category : SheetContent()

    object Tags : SheetContent()

    object Merchant : SheetContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditBottomSheet(
    sheetContent: SheetContent?,
    details: TransactionDetails,
    viewModel: TransactionViewModel,
    accountViewModel: AccountViewModel,
    onSaveRenameRule: (originalName: String, newName: String) -> Unit,
    accounts: List<Account>,
    categories: List<Category>,
    allTags: List<Tag>,
    selectedTags: Set<Tag>,
    sheetState: SheetState,
    containerColor: Color,
    onDismiss: () -> Unit,
    onAddNewAccount: () -> Unit,
    onAddNewCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sheetContent == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        containerColor = containerColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
    ) {
        TransactionEditSheetContent(
            sheetContent = sheetContent,
            details = details,
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            onSaveRenameRule = onSaveRenameRule,
            accounts = accounts,
            categories = categories,
            allTags = allTags,
            selectedTags = selectedTags,
            onDismiss = onDismiss,
            onAddNewAccount = onAddNewAccount,
            onAddNewCategory = onAddNewCategory,
        )
    }
}

@Composable
fun TransactionEditSheetContent(
    sheetContent: SheetContent,
    details: TransactionDetails,
    viewModel: TransactionViewModel,
    accountViewModel: AccountViewModel,
    onSaveRenameRule: (originalName: String, newName: String) -> Unit,
    accounts: List<Account>,
    categories: List<Category>,
    allTags: List<Tag>,
    selectedTags: Set<Tag>,
    onDismiss: () -> Unit,
    onAddNewAccount: () -> Unit,
    onAddNewCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transactionId = details.transaction.id
    val context = LocalContext.current

    when (sheetContent) {
        is SheetContent.Merchant -> {
            MerchantPredictionSheet(
                viewModel = viewModel,
                initialDescription = details.transaction.description,
                onQueryChanged = {},
                onPredictionSelected = { prediction ->
                    viewModel.updateTransactionDescription(transactionId, prediction.description)
                    prediction.categoryId?.let { catId ->
                        viewModel.updateTransactionCategory(transactionId, catId)
                    }
                    onDismiss()
                },
                onManualSave = { newDescription ->
                    viewModel.updateTransactionDescription(transactionId, newDescription)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        is SheetContent.Amount -> {
            EditTextFieldSheet(
                title = "Edit Amount",
                initialValue = "%.2f".format(details.transaction.amount),
                keyboardType = KeyboardType.Number,
                onValueChangeFilter = { newValue ->
                    if (newValue.isEmpty()) {
                        true
                    } else if ((newValue.toDoubleOrNull() ?: 0.0) <= 1_000_000_000.0) {
                        true
                    } else {
                        Toast.makeText(context, "Maximum limit of 1 Billion (1,000,000,000) reached.", Toast.LENGTH_SHORT).show()
                        false
                    }
                },
                onConfirm = {
                    viewModel.updateTransactionAmount(transactionId, it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        is SheetContent.Notes -> {
            EditTextFieldSheet(
                title = "Edit Notes",
                initialValue = details.transaction.notes ?: "",
                onConfirm = {
                    viewModel.updateTransactionNotes(transactionId, it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        is SheetContent.Account -> {
            AccountPickerBottomSheet(
                title = "Select Account",
                selectedAccountId = details.transaction.accountId,
                accounts = accounts,
                onAccountSelected = {
                    viewModel.updateTransactionAccount(transactionId, it.id)
                    onDismiss()
                },
                onDismiss = onDismiss,
                onAddNew = onAddNewAccount,
                onRenameAccount = { accountId, newName ->
                    accountViewModel.renameAccount(accountId, newName)
                },
                modifier = modifier,
            )
        }
        is SheetContent.Category -> {
            CategoryPickerBottomSheet(
                title = "Select Category",
                categories = categories,
                selectedCategoryId = details.transaction.categoryId,
                onCategorySelected = {
                    viewModel.updateTransactionCategory(transactionId, it.id)
                    onDismiss()
                },
                onDismiss = onDismiss,
                onAddNew = onAddNewCategory,
                modifier = modifier,
            )
        }
        is SheetContent.Tags -> {
            TagPickerBottomSheet(
                allTags = allTags,
                selectedTags = selectedTags,
                onTagSelected = viewModel::onTagSelected,
                onAddNewTag = viewModel::addTagOnTheGo,
                onConfirm = {
                    viewModel.updateTagsForTransaction(transactionId)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
fun EditTextFieldSheet(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChangeFilter: ((String) -> Boolean)? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    additionalContent: @Composable (() -> Unit)? = null,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val inlineToolbar = rememberInlineTextToolbar()

    CompositionLocalProvider(LocalTextToolbar provides inlineToolbar) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

            InlineTextToolbarActionBar(inlineToolbar)

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = {
                    if (onValueChangeFilter == null || onValueChangeFilter(it.text)) {
                        textFieldValue = it
                    }
                },
                label = { Text("Value") },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = keyboardType,
                        capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                    ),
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("value_input")
                        .focusRequester(focusRequester),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
            )
            additionalContent?.invoke()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onConfirm(textFieldValue.text) }) { Text("Save") }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}
