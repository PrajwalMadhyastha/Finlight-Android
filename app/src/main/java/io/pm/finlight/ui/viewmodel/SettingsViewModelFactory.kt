package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.ml.SmsEntityExtractor

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.create(application)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class SettingsViewModelFactory(
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
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideSettingsViewModel(
                context = application,
                autoSaveSmsTransactionUseCase = autoSaveSmsTransactionUseCase,
                nerExtractorProvider = nerExtractorProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        return create(modelClass)
    }
}
