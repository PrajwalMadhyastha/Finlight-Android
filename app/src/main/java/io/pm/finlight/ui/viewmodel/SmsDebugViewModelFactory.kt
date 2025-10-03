// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SmsDebugViewModelFactory.kt
// REASON: REFACTOR (Testing) - The factory has been updated to instantiate and
// provide all required repository and service dependencies to the
// SmsDebugViewModel's constructor, supporting the new dependency injection pattern.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.SmsDebugViewModel
import io.pm.finlight.SmsRepository
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ml.SmsClassifier

class SmsDebugViewModelFactory(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmsDebugViewModel::class.java)) {
            val smsRepository = SmsRepository(application)
            val db = AppDatabase.getInstance(application)
            val smsClassifier = SmsClassifier(application)

            @Suppress("UNCHECKED_CAST")
            return SmsDebugViewModel(
                application,
                smsRepository,
                db,
                smsClassifier,
                transactionViewModel
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}