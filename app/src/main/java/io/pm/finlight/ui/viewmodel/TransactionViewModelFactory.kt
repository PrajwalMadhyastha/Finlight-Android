package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.di.ServiceLocator

@Deprecated(
    message = "Migrate to FinlightViewModelFactory or ServiceLocator. Individual factories will be removed in a future release.",
    replaceWith =
        ReplaceWith(
            "FinlightViewModelFactory.create(application)",
            "io.pm.finlight.di.FinlightViewModelFactory",
        ),
)
class TransactionViewModelFactory(
    private val application: Application,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            val handle = savedStateHandle ?: runCatching { extras.createSavedStateHandle() }.getOrElse { SavedStateHandle() }
            @Suppress("UNCHECKED_CAST")
            return ServiceLocator.provideTransactionViewModel(application, savedStateHandle = handle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }
}
