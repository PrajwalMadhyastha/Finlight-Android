// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TimePeriodReportViewModelFactory.kt
// REASON: REFACTOR (Consistency) - The factory now instantiates and injects the
// `TransactionRepository` into the `TimePeriodReportViewModel`, replacing the
// direct DAO/SettingsRepo injection. This aligns it with the app's standard
// dependency injection pattern and provides access to the new centralized
// consistency logic.
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
            // --- NEW: Instantiate all repositories ---
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)

            @Suppress("UNCHECKED_CAST")
            return TimePeriodReportViewModel(
                // --- UPDATED: Pass repositories instead of DAOs ---
                transactionDao = db.transactionDao(), // Still needed for charts/insights
                transactionRepository = transactionRepository,
                timePeriod = timePeriod,
                initialDateMillis = initialDateMillis,
                showPreviousMonth = showPreviousMonth
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
