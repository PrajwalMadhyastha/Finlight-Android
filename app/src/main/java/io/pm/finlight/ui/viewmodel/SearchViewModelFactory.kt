// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SettingsViewModelFactory.kt
// REASON: FEATURE - The factory has been updated to accept a TransactionViewModel
// instance. This dependency is now required by the SettingsViewModel to delegate
// the auto-saving of transactions during a retrospective SMS scan.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SettingsViewModelFactory(
    private val application: Application,
    // --- NEW: Accept TransactionViewModel as a dependency ---
    private val transactionViewModel: TransactionViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // --- UPDATED: Pass the TransactionViewModel to the SettingsViewModel ---
            return SettingsViewModel(application, transactionViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
