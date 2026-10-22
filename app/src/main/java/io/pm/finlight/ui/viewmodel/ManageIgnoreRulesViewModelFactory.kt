package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider

class ManageIgnoreRulesViewModelFactory(
    private val application: Application,
    private val ignoreRuleRepository: IIgnoreRuleRepository? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageIgnoreRulesViewModel::class.java)) {
            val repository = ignoreRuleRepository ?: ServiceLocator.provideIgnoreRuleRepository(application)
            val dispatchers = dispatcherProvider ?: ServiceLocator.provideDispatcherProvider(application)
            @Suppress("UNCHECKED_CAST")
            return ManageIgnoreRulesViewModel(
                ignoreRuleRepository = repository,
                dispatcherProvider = dispatchers,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
