package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ui.viewmodel.LinkTransactionViewModel

class LinkTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkTransactionViewModel::class.java)) {
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            @Suppress("UNCHECKED_CAST")
            return LinkTransactionViewModel(
                transactionRepository,
                potentialTransaction,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
