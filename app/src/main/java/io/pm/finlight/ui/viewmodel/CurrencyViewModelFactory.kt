package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import io.pm.finlight.di.ServiceLocator

class CurrencyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CurrencyViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionQueryDao())
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                )
            val tripRepository = TripRepository(db.tripDao())

            @Suppress("UNCHECKED_CAST")
            return CurrencyViewModel(
                application,
                settingsRepository,
                tripRepository,
                transactionRepository,
                tagRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
