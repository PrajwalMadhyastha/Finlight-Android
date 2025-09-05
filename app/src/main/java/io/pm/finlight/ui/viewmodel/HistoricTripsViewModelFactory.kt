// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/HistoricTripsViewModelFactory.kt
// REASON: NEW FILE - A standard factory for creating instances of the
// HistoricTripsViewModel with its required dependencies.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class HistoricTripsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoricTripsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoricTripsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
