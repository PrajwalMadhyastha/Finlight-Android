package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ui.viewmodel.LinkTransactionViewModel

class LinkTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkTransactionViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val recurringTransactionDao = db.recurringTransactionDao()
            @Suppress("UNCHECKED_CAST")
            return LinkTransactionViewModel(
                transactionRepository,
                recurringTransactionDao,
                potentialTransaction,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
