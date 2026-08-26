// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/TransactionViewModelFactory.kt
// REASON: NEW FILE - This factory handles the creation of TransactionViewModel
// for the main application. It instantiates all necessary repository and DAO
// dependencies and injects them into the ViewModel's constructor. This decouples
// the ViewModel from direct database initialization, enabling easier unit testing.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase

class TransactionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
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
                    db = db,
                )

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
            return TransactionViewModel(
                application = application,
                db = db,
                transactionRepository = transactionRepository,
                accountRepository = AccountRepository(db),
                categoryRepository = CategoryRepository(db.categoryDao()),
                tagRepository = tagRepository,
                settingsRepository = settingsRepository,
                smsRepository = SmsRepository(application),
                merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao()),
                merchantCategoryMappingRepository = MerchantCategoryMappingRepository(db.merchantCategoryMappingDao()),
                merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao()),
                splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao()),
                smsParseTemplateDao = db.smsParseTemplateDao(),
                mergeTransactionsUseCase = mergeTransactionsUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
