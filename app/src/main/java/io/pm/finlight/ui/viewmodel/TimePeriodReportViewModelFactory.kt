package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.di.FinlightViewModelFactory
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
    application: Application,
    timePeriod: TimePeriod,
    initialDateMillis: Long?,
    showPreviousMonth: Boolean,
    settingsRepository: ISettingsRepository? = null,
    transactionRepository: ITransactionRepository? = null,
    getMonthlyConsistencyDataUseCase: GetMonthlyConsistencyDataUseCase? = null,
    dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory by FinlightViewModelFactory.createTimePeriodReportFactory(
        context = application,
        timePeriod = timePeriod,
        initialDateMillis = initialDateMillis,
        showPreviousMonth = showPreviousMonth,
        settingsRepository = settingsRepository,
        transactionRepository = transactionRepository,
        getMonthlyConsistencyDataUseCase = getMonthlyConsistencyDataUseCase,
        dispatcherProvider = dispatcherProvider,
    )
