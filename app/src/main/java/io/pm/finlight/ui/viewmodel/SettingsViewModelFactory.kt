// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModelFactory.kt
// REASON: NEW FILE - This factory is required to instantiate the SmsDebugViewModel
// with its new TransactionViewModel dependency, which is needed to auto-import
// transactions after a new rule is created.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SmsDebugViewModelFactory(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmsDebugViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SmsDebugViewModel(application, transactionViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
