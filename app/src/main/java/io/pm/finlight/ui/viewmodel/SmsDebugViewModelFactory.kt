// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModelFactory.kt
// REASON: FIX - The package declaration has been corrected to match the file's
// directory structure, resolving potential build errors. Imports were added for
// classes now outside the package.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.SmsDebugViewModel
import io.pm.finlight.TransactionViewModel

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
