package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.utils.SmsProviderHelper

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
            val tagRepository = ServiceLocator.provideTagRepository(application)
            val splitTransactionRepository = ServiceLocator.provideSplitTransactionRepository(application, db)
            val merchantRenameRuleRepository = ServiceLocator.provideMerchantRenameRuleRepository(application, db)
            val merchantCategoryMappingRepository = ServiceLocator.provideMerchantCategoryMappingRepository(application, db)
            val customSmsRuleRepository = ServiceLocator.provideCustomSmsRuleRepository(application, db)
            val ignoreRuleRepository = ServiceLocator.provideIgnoreRuleRepository(application, db)
            val smsParseTemplateProvider = SmsProviderHelper.getSmsParseTemplateProvider(db)
            val transactionRunner = ServiceLocator.provideTransactionRunner(application, db)
            val databaseHealthChecker = ServiceLocator.provideDatabaseHealthChecker()

            @Suppress("UNCHECKED_CAST")
            val viewModel =
                SettingsViewModel(
                    application = application,
                    settingsRepository = settingsRepository,
                    transactionRepository = transactionRepository,
                    merchantMappingRepository = merchantMappingRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    smsRepository = smsRepository,
                    autoSaveSmsTransactionUseCase = autoSaveUseCase,
                    smsClassifier = smsClassifier,
                    tagRepository = tagRepository,
                    splitTransactionRepository = splitTransactionRepository,
                    merchantRenameRuleRepository = merchantRenameRuleRepository,
                    merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                    customSmsRuleRepository = customSmsRuleRepository,
                    ignoreRuleRepository = ignoreRuleRepository,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                    nerExtractorProvider = nerExtractorProvider ?: { MlModelFactory.getNerExtractor(application) },
                    transactionRunner = transactionRunner,
                    databaseHealthChecker = databaseHealthChecker,
                    dispatchers = dispatcherProvider,
                )
            return viewModel as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
