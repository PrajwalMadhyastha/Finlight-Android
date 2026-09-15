package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.RoomTransactionRunner
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.ml.SmsClassifier

class SettingsViewModelFactory(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao())
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)
            val smsRepository = ServiceLocator.provideSmsRepository(application)
            val smsClassifier = SmsClassifier(application)
            val nerExtractor = NerExtractor(application)
            val transactionRunner = RoomTransactionRunner()

            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                application,
                settingsRepository,
                db,
                transactionRepository,
                merchantMappingRepository,
                accountRepository,
                categoryRepository,
                smsRepository,
                transactionViewModel,
                smsClassifier,
                nerExtractor,
                transactionRunner,
                dispatchers = dispatcherProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
