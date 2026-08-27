package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.AccountRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator

class AccountViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                )
            val accountRepository = AccountRepository(db)

            @Suppress("UNCHECKED_CAST")
            return AccountViewModel(
                application,
                accountRepository,
                transactionRepository,
                settingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
