package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.di.ServiceLocator

class ProfileViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(application, settingsRepository, dispatcherProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
