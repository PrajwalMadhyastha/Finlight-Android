package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.di.ServiceLocator

/**
 * Factory for creating a DashboardViewModel with a constructor that takes dependencies.
 */
@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.create(application)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideDashboardViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        return create(modelClass)
    }
}
