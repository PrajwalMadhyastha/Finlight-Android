// FILE: app/src/main/java/io/pm/finlight/OnboardingViewModelFactory.kt
// =================================================================================
// REASON: FIX - The factory now passes the application context to the
// OnboardingViewModel, which is required for the new, more reliable currency
// detection logic that uses the TelephonyManager.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase

/**
 * Factory for creating an OnboardingViewModel.
 */
class OnboardingViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val categoryRepository = CategoryRepository(db.categoryDao())
            val settingsRepository = SettingsRepository(application)

            @Suppress("UNCHECKED_CAST")
            // --- UPDATED: Pass the application context to the ViewModel ---
            return OnboardingViewModel(application, categoryRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
