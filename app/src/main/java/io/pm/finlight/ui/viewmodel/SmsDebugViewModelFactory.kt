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
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.SmsEntityExtractor

class SmsDebugViewModelFactory(
    private val application: Application,
    private val autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase? = null,
    private val nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
) : ViewModelProvider.Factory {
    constructor(
        application: Application,
        nerExtractorProvider: (() -> SmsEntityExtractor)?,
    ) : this(
        application = application,
        autoSaveSmsTransactionUseCase = null,
        nerExtractorProvider = nerExtractorProvider,
    )

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmsDebugViewModel::class.java)) {
            val smsRepository = ServiceLocator.provideSmsRepository(application)
            val db = AppDatabase.getInstance(application)
            val smsClassifier = MlModelFactory.getClassifier(application)
            val autoSaveUseCase =
                autoSaveSmsTransactionUseCase
                    ?: ServiceLocator.provideAutoSaveSmsTransactionUseCase(application, db)

            @Suppress("UNCHECKED_CAST")
            val viewModel =
                SmsDebugViewModel(
                    application = application,
                    smsRepository = smsRepository,
                    db = db,
                    smsClassifier = smsClassifier,
                    nerExtractorProvider = nerExtractorProvider ?: { MlModelFactory.getNerExtractor(application) },
                    autoSaveSmsTransactionUseCase = autoSaveUseCase,
                )
            return viewModel as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
