// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TimePeriodReportViewModelFactory.kt
// REASON: FIX - The factory now accepts a `showPreviousMonth` boolean parameter.
// This allows it to correctly instantiate the ViewModel with the necessary flag
// to display the previous month's data when navigated to from a monthly summary
// notification.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.TimePeriod

class TimePeriodReportViewModelFactory(
    private val application: Application,
    private val timePeriod: TimePeriod,
    private val initialDateMillis: Long?,
    private val showPreviousMonth: Boolean // --- NEW: Add parameter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimePeriodReportViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            @Suppress("UNCHECKED_CAST")
            return TimePeriodReportViewModel(
                transactionDao = db.transactionDao(),
                settingsRepository = settingsRepository,
                timePeriod = timePeriod,
                initialDateMillis = initialDateMillis,
                showPreviousMonth = showPreviousMonth // --- UPDATED: Pass to ViewModel
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
