package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.TripRepository
import io.pm.finlight.utils.DefaultDispatcherProvider

class TripDetailViewModelFactory(
    private val application: Application,
    private val tripId: Int,
    private val tagId: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripDetailViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val tripRepository = TripRepository(db.tripDao())
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    db = db,
                    dispatcherProvider = DefaultDispatcherProvider(),
                )

            @Suppress("UNCHECKED_CAST")
            return TripDetailViewModel(
                tripRepository,
                transactionRepository,
                tripId,
                tagId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
