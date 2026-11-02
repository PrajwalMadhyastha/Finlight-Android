package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.utils.DispatcherProvider

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.createTimePeriodReportFactory(application, timePeriod, initialDateMillis, showPreviousMonth)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class TimePeriodReportViewModelFactory(
    private val application: Application,
    private val timePeriod: TimePeriod,
    private val initialDateMillis: Long?,
    private val showPreviousMonth: Boolean,
    private val settingsRepository: ISettingsRepository? = null,
    private val transactionRepository: ITransactionRepository? = null,
    private val getMonthlyConsistencyDataUseCase: GetMonthlyConsistencyDataUseCase? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(TimePeriodReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideTimePeriodReportViewModel(
                context = application,
                timePeriod = timePeriod,
                initialDateMillis = initialDateMillis,
                showPreviousMonth = showPreviousMonth,
                settingsRepository = settingsRepository,
                transactionRepository = transactionRepository,
                getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
