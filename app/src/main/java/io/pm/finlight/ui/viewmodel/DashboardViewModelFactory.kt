package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.utils.SystemTimeProvider

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val getMonthlyConsistencyDataUseCase =
                GetMonthlyConsistencyDataUseCase(
                    settingsRepository = settingsRepository,
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                )
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                )
            val accountRepository = AccountRepository(db)
            val merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
            val mergeTransactionsUseCase =
                MergeTransactionsUseCase(
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    mergeRecordDao = db.mergeRecordDao(),
                    deletedSmsHashDao = db.deletedSmsHashDao(),
                    db = db,
                )

            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                budgetDao = db.budgetDao(),
                settingsRepository = settingsRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                timeProvider = SystemTimeProvider(),
                recurringTransactionDao = db.recurringTransactionDao(),
                recurringPatternDao = db.recurringPatternDao(),
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
