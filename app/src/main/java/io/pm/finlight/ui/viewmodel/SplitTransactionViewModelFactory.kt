package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.DefaultDispatcherProvider

class SplitTransactionViewModelFactory(
    private val application: Application,
    private val transactionId: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SplitTransactionViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                    dispatcherProvider = DefaultDispatcherProvider(),
                )
            val categoryRepository = CategoryRepository(db.categoryDao())
            val splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao())

            @Suppress("UNCHECKED_CAST")
            return SplitTransactionViewModel(
                transactionRepository = transactionRepository,
                categoryRepository = categoryRepository,
                splitTransactionRepository = splitTransactionRepository,
                transactionId = transactionId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
