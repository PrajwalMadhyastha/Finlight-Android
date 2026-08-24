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
    // --- NEW: Add parameter
    private val showPreviousMonth: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimePeriodReportViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            // --- NEW: Instantiate all repositories ---
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionQueryDao())
            val transactionRepository =
                TransactionRepository(
                    transactionWriteDao = db.transactionWriteDao(),
                    transactionQueryDao = db.transactionQueryDao(),
                    transactionAnalyticsDao = db.transactionAnalyticsDao(),
                    transactionReimbursementDao = db.transactionReimbursementDao(),
                    settingsRepository = settingsRepository,
                    tagRepository = tagRepository,
                    deletedSmsHashDao = db.deletedSmsHashDao(),
                    mergeRecordDao = db.mergeRecordDao(),
                    db = db,
                )

            @Suppress("UNCHECKED_CAST")
            return TimePeriodReportViewModel(
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                transactionRepository = transactionRepository,
                settingsRepository = settingsRepository,
                timePeriod = timePeriod,
                initialDateMillis = initialDateMillis,
                showPreviousMonth = showPreviousMonth,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
