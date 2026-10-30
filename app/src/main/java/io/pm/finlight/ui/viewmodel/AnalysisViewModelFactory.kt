package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator

class AnalysisViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            @Suppress("UNCHECKED_CAST")
            return AnalysisViewModel(
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                categoryDao = db.categoryDao(),
                tagDao = db.tagDao(),
                dispatcherProvider = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
