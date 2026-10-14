package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.RoomTransactionRunner
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.SmsEntityExtractor

class SettingsViewModelFactory(
    private val application: Application,
    private val autoSaveSmsTransactionUseCase: AutoSaveSmsTransactionUseCase? = null,
    private val nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
) : ViewModelProvider.Factory {
    constructor(
        application: Application,
        nerExtractorProvider: (() -> SmsEntityExtractor)?,
    ) : this(
        application = application,
        autoSaveSmsTransactionUseCase = null,
        nerExtractorProvider = nerExtractorProvider,
    )

    constructor(
        application: Application,
        transactionViewModel: TransactionViewModel,
        nerExtractorProvider: (() -> SmsEntityExtractor)? = null,
    ) : this(
        application = application,
        autoSaveSmsTransactionUseCase = null,
        nerExtractorProvider = nerExtractorProvider,
    )

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = ServiceLocator.provideSettingsRepository(application)
            val dispatcherProvider = ServiceLocator.provideDispatcherProvider(application)
            val transactionRepository = ServiceLocator.provideTransactionRepository(application)
            val merchantMappingRepository = ServiceLocator.provideMerchantMappingRepository(application, db)
            val accountRepository = ServiceLocator.provideAccountRepository(application)
            val categoryRepository = ServiceLocator.provideCategoryRepository(application)
            val smsRepository = ServiceLocator.provideSmsRepository(application)
            val autoSaveUseCase =
                autoSaveSmsTransactionUseCase
                    ?: ServiceLocator.provideAutoSaveSmsTransactionUseCase(application, db)
            val smsClassifier = MlModelFactory.getClassifier(application)
            val transactionRunner = RoomTransactionRunner()

            @Suppress("UNCHECKED_CAST")
            val viewModel =
                if (nerExtractorProvider != null) {
                    SettingsViewModel(
                        application,
                        settingsRepository,
                        db,
                        transactionRepository,
                        merchantMappingRepository,
                        accountRepository,
                        categoryRepository,
                        smsRepository,
                        autoSaveUseCase,
                        smsClassifier,
                        nerExtractorProvider = nerExtractorProvider,
                        transactionRunner = transactionRunner,
                        dispatchers = dispatcherProvider,
                    )
                } else {
                    SettingsViewModel(
                        application,
                        settingsRepository,
                        db,
                        transactionRepository,
                        merchantMappingRepository,
                        accountRepository,
                        categoryRepository,
                        smsRepository,
                        autoSaveUseCase,
                        smsClassifier,
                        transactionRunner = transactionRunner,
                        dispatchers = dispatcherProvider,
                    )
                }
            return viewModel as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
