package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

class RuleCreationViewModelFactory(
    private val application: Application,
    private val customSmsRuleRepository: ICustomSmsRuleRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(RuleCreationViewModel::class.java)) {
            val repository = customSmsRuleRepository ?: ServiceLocator.provideCustomSmsRuleRepository(application)
            val dispatchers = dispatcherProvider ?: ServiceLocator.provideDispatcherProvider(application)
            val handle = savedStateHandle ?: runCatching { extras.createSavedStateHandle() }.getOrElse { SavedStateHandle() }
            @Suppress("UNCHECKED_CAST")
            return RuleCreationViewModel(
                customSmsRuleRepository = repository,
                dispatcherProvider = dispatchers,
                savedStateHandle = handle,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create(modelClass, CreationExtras.Empty)
    }
}
