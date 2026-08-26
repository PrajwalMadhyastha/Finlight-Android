package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ReportsViewModel
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase

class ReportsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportsViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
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

            @Suppress("UNCHECKED_CAST")
            return ReportsViewModel(
                transactionRepository = transactionRepository,
                categoryDao = db.categoryDao(),
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
