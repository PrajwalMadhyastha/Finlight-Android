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
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.SmsEntityExtractor

class SmsDebugViewModelFactory(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel,
    private val nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmsDebugViewModel::class.java)) {
            val smsRepository = ServiceLocator.provideSmsRepository(application)
            val db = AppDatabase.getInstance(application)
            val smsClassifier = MlModelFactory.getClassifier(application)

            @Suppress("UNCHECKED_CAST")
            val viewModel =
                if (nerExtractorProvider != null) {
                    SmsDebugViewModel(
                        application = application,
                        smsRepository = smsRepository,
                        db = db,
                        smsClassifier = smsClassifier,
                        nerExtractorProvider = nerExtractorProvider,
                        transactionViewModel = transactionViewModel,
                    )
                } else {
                    SmsDebugViewModel(
                        application = application,
                        smsRepository = smsRepository,
                        db = db,
                        smsClassifier = smsClassifier,
                        transactionViewModel = transactionViewModel,
                    )
                }
            return viewModel as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
