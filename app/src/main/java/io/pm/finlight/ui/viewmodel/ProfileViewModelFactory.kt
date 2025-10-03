// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/ProfileViewModelFactory.kt
// REASON: NEW FILE - This factory handles the creation of ProfileViewModel,
// injecting the SettingsRepository to support dependency injection and enable
// unit testing.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.ProfileViewModel
import io.pm.finlight.SettingsRepository

class ProfileViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            val settingsRepository = SettingsRepository(application)
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(application, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}