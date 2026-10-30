package io.pm.finlight.ui.viewmodel.delegate

import android.util.Log
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.SplitTransaction
import io.pm.finlight.SplitTransactionDetails
import io.pm.finlight.Transaction
import io.pm.finlight.ui.viewmodel.SplitItem
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionSplitDelegate(
    private val coroutineScope: CoroutineScope,
    private val splitTransactionRepository: ISplitTransactionRepository,
    private val transactionRepository: ITransactionRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    fun getSplitDetailsForTransaction(transactionId: Int): Flow<List<SplitTransactionDetails>> {
        return splitTransactionRepository.getSplitsForParent(transactionId)
    }

    fun saveTransactionSplits(
        parentTransactionId: Int,
        splitItems: List<SplitItem>,
        onComplete: (Boolean) -> Unit = {},
    ) {
        coroutineScope.launch(dispatcherProvider.io) {
            try {
                val parentTxn =
                    transactionRepository.getTransactionById(parentTransactionId).firstOrNull() ?: run {
                        withContext(dispatcherProvider.main) {
                            onComplete(false)
                        }
                        return@launch
                    }
                val conversionRate = parentTxn.conversionRate ?: 1.0

                val newSplits =
                    splitItems.map {
                        val originalAmount = it.amount.toDoubleOrNull() ?: 0.0
                        SplitTransaction(
                            parentTransactionId = parentTransactionId,
                            amount = originalAmount * conversionRate,
                            originalAmount = if (parentTxn.currencyCode != null) originalAmount else null,
                            categoryId = it.category?.id,
                            notes = it.notes,
                        )
                    }

                splitTransactionRepository.saveSplits(parentTransactionId, newSplits)

                withContext(dispatcherProvider.main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e("TransactionSplitDelegate", "Error saving transaction splits", e)
                withContext(dispatcherProvider.main) {
                    onComplete(false)
                }
            }
        }
    }

    fun unsplitTransaction(
        transaction: Transaction,
        onError: (suspend (String) -> Unit)? = null,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        coroutineScope.launch(dispatcherProvider.io) {
            try {
                val originalDescription = transaction.originalDescription ?: transaction.description
                splitTransactionRepository.unmarkSplit(transaction.id, originalDescription)
                if (onComplete != null) {
                    withContext(dispatcherProvider.main) {
                        onComplete(true)
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionSplitDelegate", "Error unmarking transaction split", e)
                onError?.invoke("Failed to un-split transaction.")
                if (onComplete != null) {
                    withContext(dispatcherProvider.main) {
                        onComplete(false)
                    }
                }
            }
        }
    }
}
