// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DashboardViewModelFactory.kt
// REASON: REFACTOR - The factory now passes the full AppDatabase instance to the
// AccountRepository. This is required to support the new transactional account
// merging logic.
// FIX (Race Condition) - The factory now also passes the SettingsRepository and
// TagRepository to the TransactionRepository. This is required for the new
// centralized, atomic travel mode tagging logic.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.SystemTimeProvider

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionQueryDao())
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    settingsRepository = settingsRepository,
                    tagRepository = tagRepository,
                    deletedSmsHashDao = db.deletedSmsHashDao(),
                    mergeRecordDao = db.mergeRecordDao(),
                    db = db,
                )
            val accountRepository = AccountRepository(db)
            val merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
            val getMonthlyConsistencyDataUseCase = io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase(settingsRepository, transactionRepository)
            val mergeTransactionsUseCase =
                io.pm.finlight.domain.usecase.MergeTransactionsUseCase(
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
                smsRepository = SmsRepository(application),
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
