package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

class ManageParseRulesViewModelFactory(
    private val application: Application,
    private val customSmsRuleRepository: ICustomSmsRuleRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageParseRulesViewModel::class.java)) {
            val repository = customSmsRuleRepository ?: ServiceLocator.provideCustomSmsRuleRepository(application)
            val dispatchers = dispatcherProvider ?: ServiceLocator.provideDispatcherProvider(application)
            @Suppress("UNCHECKED_CAST")
            return ManageParseRulesViewModel(
                customSmsRuleRepository = repository,
                dispatcherProvider = dispatchers,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
