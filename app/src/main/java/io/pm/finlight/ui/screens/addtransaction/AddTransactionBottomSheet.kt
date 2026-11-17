package io.pm.finlight.ui.screens.addtransaction

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.MerchantPredictionSheet
import io.pm.finlight.ui.components.pickers.AccountPickerBottomSheet
import io.pm.finlight.ui.components.pickers.CategoryPickerBottomSheet
import io.pm.finlight.ui.components.pickers.TagPickerBottomSheet

sealed class ComposerSheet {
    object Category : ComposerSheet()

    object Account : ComposerSheet()

    object Tags : ComposerSheet()

    object Notes : ComposerSheet()

    object Merchant : ComposerSheet()

    object History : ComposerSheet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    activeSheet: ComposerSheet?,
    onDismissSheet: () -> Unit,
    sheetState: SheetState,
    containerColor: Color,
    accounts: List<Account>,
    categories: List<Category>,
    selectedCategory: Category?,
    allTags: List<Tag>,
    selectedTags: Set<Tag>,
    notes: String,
    historyTransactions: List<TransactionDetails>,
    merchantDescription: String,
    viewModel: TransactionViewModel,
    isCategoryNudgeActive: Boolean,
    onCategoryNudgeSave: (Long?) -> Unit,
    onNotesConfirmed: (String) -> Unit,
    onQuickFillSelected: (TransactionDetails) -> Unit,
    onOpenCreateAccount: () -> Unit,
    onOpenCreateCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = {
                if (isCategoryNudgeActive) {
                    viewModel.saveWithSelectedCategory(null) { txnId ->
                        onCategoryNudgeSave(txnId)
                    }
                }
                onDismissSheet()
            },
            sheetState = sheetState,
            windowInsets = WindowInsets(0),
            containerColor = containerColor,
            modifier = modifier,
        ) {
            when (activeSheet) {
                is ComposerSheet.Account ->
                    AccountPickerBottomSheet(
                        accounts = accounts,
                        onAccountSelected = {
                            viewModel.onAddTransactionAccountChanged(it)
                            onDismissSheet()
                        },
                        onAddNew = {
                            onOpenCreateAccount()
                            onDismissSheet()
                        },
                    )
                is ComposerSheet.Category ->
                    CategoryPickerBottomSheet(
                        title = "Select Category",
                        categories = categories,
                        selectedCategoryId = selectedCategory?.id,
                        onCategorySelected = {
                            viewModel.onAddTransactionCategoryChanged(it)
                            if (isCategoryNudgeActive) {
                                viewModel.saveWithSelectedCategory(it.id) { txnId ->
                                    onCategoryNudgeSave(txnId)
                                }
                            }
                            onDismissSheet()
                        },
                        onAddNew = {
                            onOpenCreateCategory()
                            onDismissSheet()
                        },
                    )
                is ComposerSheet.Tags ->
                    TagPickerBottomSheet(
                        allTags = allTags,
                        selectedTags = selectedTags,
                        onTagSelected = viewModel::onTagSelected,
                        onAddNewTag = viewModel::addTagOnTheGo,
                        onConfirm = onDismissSheet,
                        onDismiss = onDismissSheet,
                    )
                is ComposerSheet.Notes ->
                    TextInputSheet(
                        title = "Add Notes",
                        initialValue = notes,
                        onConfirm = {
                            onNotesConfirmed(it)
                            onDismissSheet()
                        },
                    )
                is ComposerSheet.Merchant ->
                    MerchantPredictionSheet(
                        viewModel = viewModel,
                        initialDescription = merchantDescription,
                        onQueryChanged = { viewModel.onAddTransactionDescriptionChanged(it) },
                        onPredictionSelected = { prediction ->
                            viewModel.onAddTransactionDescriptionChanged(prediction.description)
                            prediction.categoryId?.let { catId ->
                                val cat = categories.find { it.id == catId }
                                viewModel.onAddTransactionCategoryChanged(cat)
                            }
                            prediction.accountId?.let { accId ->
                                val acc = accounts.find { it.id == accId }
                                viewModel.onAddTransactionAccountChanged(acc)
                            }
                            onDismissSheet()
                        },
                        onManualSave = { newDescription ->
                            viewModel.onAddTransactionDescriptionChanged(newDescription)
                            onDismissSheet()
                        },
                        onDismiss = onDismissSheet,
                    )
                is ComposerSheet.History ->
                    TransactionHistorySheet(
                        transactions = historyTransactions,
                        onTransactionSelected = { txn ->
                            onQuickFillSelected(txn)
                            onDismissSheet()
                        },
                    )
            }
        }
    }
}
