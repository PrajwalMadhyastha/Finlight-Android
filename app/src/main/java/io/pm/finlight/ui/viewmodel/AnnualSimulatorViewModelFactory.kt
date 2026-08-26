package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionRepository

class AnnualSimulatorViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnnualSimulatorViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = database.transactionWriteDao(),
                    transactionQueryDao = database.transactionQueryDao(),
                    transactionAnalyticsDao = database.transactionAnalyticsDao(),
                    transactionReimbursementDao = database.transactionReimbursementDao(),
                    db = database,
                )
            @Suppress("UNCHECKED_CAST")
            return AnnualSimulatorViewModel(transactionRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
