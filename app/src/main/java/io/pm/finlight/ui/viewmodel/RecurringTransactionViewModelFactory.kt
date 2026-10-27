// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/RecurringTransactionViewModelFactory.kt
// REASON: NEW FILE - This factory provides the Application context and
// RecurringTransactionRepository dependency to the RecurringTransactionViewModel,
// enabling constructor injection for better testability.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.di.ServiceLocator

class RecurringTransactionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecurringTransactionViewModel::class.java)) {
            val repository = ServiceLocator.provideRecurringTransactionRepository(application)
            val scheduleWorkerUseCase = ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application)
            @Suppress("UNCHECKED_CAST")
            return RecurringTransactionViewModel(repository, scheduleWorkerUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
